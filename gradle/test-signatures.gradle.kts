import org.gradle.api.tasks.testing.Test
import java.net.URLClassLoader
import java.lang.reflect.Modifier

// Validate compiled signatures before JUnit can silently omit invalid test methods.
allprojects {
    tasks.withType<Test>().configureEach {
        doFirst {
            logger.lifecycle("Auditing JUnit signatures for $path in ${testClassesDirs.files}")
            val annotations = setOf(
                "org.junit.jupiter.api.Test",
                "org.junit.jupiter.api.RepeatedTest",
                "org.junit.jupiter.api.TestTemplate",
                "org.junit.jupiter.params.ParameterizedTest",
                "org.junit.Test",
            )
            URLClassLoader(
                (classpath.files + testClassesDirs.files).map { it.toURI().toURL() }.toTypedArray(),
                ClassLoader.getPlatformClassLoader(),
            ).use { loader ->
                fun isTest(type: Class<out Annotation>, visited: MutableSet<String> = mutableSetOf()): Boolean =
                    visited.add(type.name) &&
                        (type.name in annotations || type.annotations.any { isTest(it.annotationClass.java, visited) })

                val invalid = testClassesDirs.files.flatMap { root ->
                    if (!root.exists()) emptyList() else root.walkTopDown()
                        .filter { it.isFile && it.extension == "class" && it.name != "module-info.class" }
                        .flatMap { file ->
                            val name = file.relativeTo(root).path.removeSuffix(".class").replace(java.io.File.separatorChar, '.')
                            Class.forName(name, false, loader).declaredMethods.asSequence()
                                .filter { method -> method.annotations.any { isTest(it.annotationClass.java) } }
                                .filter { method ->
                                    method.returnType != Void.TYPE || Modifier.isPrivate(method.modifiers) ||
                                        Modifier.isStatic(method.modifiers)
                                }.map { method -> "$name.${method.name}: ${method.returnType.name}" }
                        }.toList()
                }
                check(invalid.isEmpty()) {
                    "Non-discoverable JUnit test signatures (use an explicit Unit return type):\n" + invalid.joinToString("\n")
                }
            }
        }
    }
}
