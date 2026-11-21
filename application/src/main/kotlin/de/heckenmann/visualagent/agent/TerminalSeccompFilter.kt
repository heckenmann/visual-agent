package de.heckenmann.visualagent.agent

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds the classic BPF program consumed by Bubblewrap's Linux x86-64 seccomp option. */
internal object TerminalSeccompFilter {
    /** Rejects alternate syscall ABIs and denies host socket creation and io_uring. */
    fun x86Filter(): ByteArray {
        val code = ByteBuffer.allocate(12 * 8).order(ByteOrder.LITTLE_ENDIAN)
        code.instruction(LOAD_WORD, operand = 4) // seccomp_data.arch
        code.instruction(JUMP_EQUAL, onTrue = 1, operand = AUDIT_ARCH_X86_64)
        code.instruction(RETURN, operand = KILL_PROCESS)
        code.instruction(LOAD_WORD, operand = 0) // seccomp_data.nr

        // x32 shares the audit architecture but marks its syscall numbers with this bit.
        code.instruction(JUMP_AT_LEAST, onFalse = 1, operand = X32_SYSCALL_BIT)
        code.instruction(RETURN, operand = KILL_PROCESS)
        code.instruction(JUMP_EQUAL, onTrue = 4, operand = 41) // socket
        code.instruction(JUMP_EQUAL, onTrue = 3, operand = 42) // connect
        code.instruction(JUMP_EQUAL, onTrue = 2, operand = 425) // io_uring_setup
        code.instruction(JUMP_EQUAL, onTrue = 1, operand = 426) // io_uring_enter
        code.instruction(RETURN, operand = ALLOW)
        code.instruction(RETURN, operand = RETURN_EPERM)
        return code.array()
    }

    private fun ByteBuffer.instruction(
        operation: Short,
        onTrue: Byte = 0,
        onFalse: Byte = 0,
        operand: Int,
    ) {
        putShort(operation).put(onTrue).put(onFalse).putInt(operand)
    }

    private const val LOAD_WORD: Short = 0x20
    private const val JUMP_EQUAL: Short = 0x15
    private const val JUMP_AT_LEAST: Short = 0x35
    private const val RETURN: Short = 0x06
    private const val AUDIT_ARCH_X86_64 = 0xc000003e.toInt()
    private const val X32_SYSCALL_BIT = 0x40000000
    private const val KILL_PROCESS = Int.MIN_VALUE
    private const val ALLOW = 0x7fff0000
    private const val RETURN_EPERM = 0x00050001
}
