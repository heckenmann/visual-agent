import org.gradle.api.tasks.testing.Test
import java.net.URLClassLoader

// Parse bytecode without loading test classes: the toolchain may be newer than Gradle's JVM.
allprojects {
    tasks.withType<Test>().configureEach {
        doFirst {
            val annotations = setOf(
                "Lorg/junit/jupiter/api/Test;", "Lorg/junit/jupiter/api/RepeatedTest;",
                "Lorg/junit/jupiter/api/TestTemplate;", "Lorg/junit/jupiter/params/ParameterizedTest;", "Lorg/junit/Test;",
            )
            val asmJars = requireNotNull(gradle.gradleHomeDir).resolve("lib").listFiles().orEmpty()
                .filter { it.name.startsWith("asm-") && it.extension == "jar" }
            check(asmJars.isNotEmpty()) { "Gradle's bundled ASM parser is missing" }
            // Isolate Gradle's bundled parser from the script's filtered compilation classpath.
            URLClassLoader(asmJars.map { it.toURI().toURL() }.toTypedArray(), ClassLoader.getPlatformClassLoader()).use { asm ->
                val readerType = asm.loadClass("org.objectweb.asm.ClassReader")
                val visitorType = asm.loadClass("org.objectweb.asm.ClassVisitor")
                val nodeType = asm.loadClass("org.objectweb.asm.tree.ClassNode")
                val readerConstructor = readerType.getConstructor(ByteArray::class.java)
                val nodeConstructor = nodeType.getConstructor()
                val accept = readerType.getMethod("accept", visitorType, Int::class.javaPrimitiveType)
                fun parse(bytes: ByteArray): Any = nodeConstructor.newInstance().also { node ->
                    accept.invoke(readerConstructor.newInstance(bytes), node, 7) // Skip code, debug data, and frames.
                }
                fun field(node: Any, name: String): Any? = node.javaClass.getField(name).get(node)
                fun annotationNames(node: Any): List<String> =
                    listOf("visibleAnnotations", "invisibleAnnotations").flatMap { name ->
                        (field(node, name) as? List<*>)?.filterNotNull()?.map { field(it, "desc") as String }.orEmpty()
                    }
                val invalid = mutableListOf<String>()
                var audited = 0
                URLClassLoader(
                    (classpath.files + testClassesDirs.files).map { it.toURI().toURL() }.toTypedArray(),
                    ClassLoader.getPlatformClassLoader(),
                ).use { resources ->
                    val metadata = mutableMapOf<String, List<String>>()
                    fun isTest(descriptor: String, visited: MutableSet<String> = mutableSetOf()): Boolean {
                        if (!visited.add(descriptor)) return false
                        if (descriptor in annotations) return true
                        val parents = metadata.getOrPut(descriptor) {
                            resources.getResourceAsStream(descriptor.removePrefix("L").removeSuffix(";") + ".class")
                                ?.use { annotationNames(parse(it.readBytes())) }.orEmpty()
                        }
                        return parents.any { isTest(it, visited) }
                    }
                    testClassesDirs.files.filter { it.exists() }.forEach { root ->
                        root.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach { file ->
                            val node = parse(file.readBytes())
                            (field(node, "methods") as List<*>).filterNotNull().forEach { method ->
                                if (annotationNames(method).any { isTest(it) }) {
                                    audited++
                                    val descriptor = field(method, "desc") as String
                                    val access = field(method, "access") as Int
                                    if (!descriptor.endsWith(")V") || access and 10 != 0) { // ACC_PRIVATE | ACC_STATIC.
                                        invalid += "${(field(node, "name") as String).replace('/', '.')}.${field(method, "name")}: $descriptor"
                                    }
                                }
                            }
                        }
                    }
                }
                logger.lifecycle("Audited $audited JUnit test signatures for $path")
                check(invalid.isEmpty()) {
                    "Non-discoverable JUnit test signatures (use an explicit Unit return type):\n" + invalid.joinToString("\n")
                }
            }
        }
    }
}
