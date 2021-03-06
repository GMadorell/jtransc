/*
 * Copyright 2016 Carlos Ballesteros Velasco
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jtransc.vfs

import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.*

fun String.toBuffer(encoding: Charset = UTF8): ByteBuffer = encoding.toBuffer(this)
fun ByteBuffer.toString(encoding: Charset): String = encoding.toString(this)

fun Charset.toBuffer(_data: String): ByteBuffer = this.encode(_data)
fun Charset.toString(data: ByteBuffer): String = this.toString(data.getBytes())

fun Charset.toBytes(data: String) = data.toByteArray(this)
fun Charset.toString(data: ByteArray) = data.toString(this)
//fun Charset.toString(data: ByteBuffer):String = data.getBytes().toString(this)

fun ByteBuffer.length(): Int = this.limit()

fun ByteBuffer.getBytes(): ByteArray {
    val out = ByteArray(this.length())
    for (n in 0..out.size - 1) out[n] = this.get(n)
    return out
}

fun ByteArray.toBuffer(): ByteBuffer = ByteBuffer.wrap(this)

object ByteBufferUtils {
    fun copy(from: ByteBuffer, fromOffset: Int, to: ByteBuffer, toOffset: Int, size: Int) {
        for (n in 0..size - 1) to.put(toOffset + n, from[fromOffset + n])
    }

    fun combine(buffers: Iterable<ByteBuffer>): ByteBuffer {
        val totalLength = buffers.sumBy { it.length() }
        val out = ByteBuffer.allocateDirect(totalLength)
        var pos = 0
        for (buffer in buffers) {
            copy(buffer, 0, out, pos, buffer.length())
            pos += buffer.length()
        }
        return out
    }
}

inline fun <T> chdirTemp(path: String, callback: () -> T): T {
    val old = RawIo.cwd()
    RawIo.chdir(path)
    try {
        return callback();
    } finally {
        RawIo.chdir(old)
    }
}

data class ProcessResult(val output: ByteBuffer, val error: ByteBuffer, val exitCode: Int) {
    val outputString: String get() = output.toString(UTF8).trim()
    val errorString: String get() = error.toString(UTF8).trim()
	val success: Boolean get() = exitCode == 0
}

/*
data class Encoding(val name: String) {
    public val charset: Charset = java.nio.charset.Charset.forName(name)
    fun toBytes(data: String) = data.toByteArray(charset)
    fun toString(data: ByteArray) = data.toString(charset)
    fun toString(data: ByteBuffer) = data.getBytes().toString(charset)
}
*/

val UTF8 = Charsets.UTF_8

class StopExecException() : Exception()


object RawIo {
    private var userDir = System.getProperty("user.dir")
    //private var userDir = File(".").getCanonicalPath()

    fun fileRead(path: String): ByteBuffer {
        //println("RawIo.fileRead($path)")
        return ByteBuffer.wrap(File(path).readBytes())
    }

    fun fileWrite(path: String, data: ByteBuffer): Unit {
        //println("RawIo.fileWrite($path, ${data.length()} bytes)")
        File(path).writeBytes(data.getBytes())
    }

    fun listdir(path: String): Array<File> {
        val file = File(path)
        if (!file.exists()) {
            throw FileNotFoundException("Can't find $path")
        }
        return file.listFiles()
    }

    fun fileExists(path: String): Boolean {
        return File(path).exists()
    }

    fun rmdir(path: String): Unit {
        File(path).delete()
    }

    fun fileRemove(path: String): Unit {
        File(path).delete()
    }

    fun fileStat(path: String): File = File(path)

    fun setMtime(path: String, time: Date) {
        File(path).setLastModified(time.time)
    }

    fun cwd(): String = userDir
    fun script(): String = userDir
    fun chdir(path: String) {
        userDir = File(path).canonicalPath
    }

    fun execOrPassthruSync(path: String, cmd: String, args: List<String>, options: ExecOptions): ProcessResult {
        return if (options.passthru) {
            passthruSync(path, cmd, args, options.filter)
        } else {
            execSync(path, cmd, args)
        }
    }

    fun execSync(path: String, cmd: String, args: List<String>): ProcessResult {
        val ps = ProcessBuilder(listOf(cmd) + args)
        ps.directory(File(path))
        ps.redirectErrorStream(false)

        val process = ps.start()

        val bw = process.outputStream.writer()
        bw.write("yes\n")
        bw.flush()

        val output = process.inputStream.readBytes().toBuffer()
        val error = process.errorStream.readBytes().toBuffer()

        return ProcessResult(output, error, process.exitValue())
    }

    fun passthruSync(path: String, cmd: String, args: List<String>, filter: ((line: String) -> Boolean)? = null): ProcessResult {
        val ps = ProcessBuilder(listOf(cmd) + args)
        ps.directory(File(path))
        ps.redirectErrorStream(true)

        val process = ps.start()

        val os = BufferedReader(InputStreamReader(process.inputStream))
        var out = ""

        try {
            while (true) {
                val line = os.readLine () ?: break
	            out += "$line\n"
                if (filter == null || filter(line)) {
                    //println("Stdout: $line")
                    println(line)
                }
            }
        } catch (e: StopExecException) {
            //Runtime.getRuntime().exec("kill -SIGINT ${(process as UNIXProcess)}");

            process.destroy()
        }
        return ProcessResult(out.toBuffer(), "".toBuffer(), process.exitValue())
    }

    fun mkdir(path: String) {
        File(path).mkdir()
    }
}


class BufferReader(val buffer: ByteBuffer) {
    //val rb = buffer.order(ByteOrder.LITTLE_ENDIAN)
    val rb = buffer.order(ByteOrder.BIG_ENDIAN)

    var offset = 0
    val length: Int get() = buffer.length()

    private fun move(count: Int): Int {
        val out = offset
        offset += count
        return out
    }

    fun dump() {
        for (n in 0..31) {
            println(Integer.toHexString(buffer.get(n).toInt() and 0xFF))
        }
    }

    fun f32(): Double = rb.getFloat(move(4)).toDouble()
    fun i32(): Int = rb.getInt(move(4))
    fun i16(): Int = rb.getShort(move(2)).toInt()
    fun i8(): Int = rb.get(move(1)).toInt()
}

fun BufferReader.buffer(len: Int): ByteBuffer {
    val out = ByteBuffer.allocateDirect(len)
    for (n in 0..len - 1) out.put(n, this.i8().toByte())
    return out
}

fun BufferReader.strRaw(len: Int): String = buffer(len).toString(UTF8)
fun BufferReader.u32(): Int = i32() // 31 bit
fun BufferReader.u16(): Int = i16() and 0xFFFF
fun BufferReader.u8(): Int = i8() and 0xFF

fun BufferReader.fs8() = u8().toDouble() / 255.0
fun BufferReader.rgba8(): Int = i32()
fun BufferReader.quality(): Int = u8()
fun BufferReader.str(): String = strRaw(u16())
fun BufferReader.bool(): Boolean = u8() != 0
fun BufferReader.boolInt(): Int = if (u8() != 0) 1 else 0

fun ByteBuffer.reader(): BufferReader = BufferReader(this)