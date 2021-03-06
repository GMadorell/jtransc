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

package com.jtransc.text

import com.jtransc.error.InvalidOperationException
import com.jtransc.error.invalidOp
import java.io.Reader

class StrReader(val str:String) {
	var offset:Int = 0
	val length: Int get() = str.length
	val eof: Boolean get() = offset >= length
	val hasMore: Boolean get() = !eof
	fun readch():Char = this.str[offset++]
	fun read():Int = if (!eof) readch().toInt() else -1
	fun peek(count:Int):String {
		return this.str.substring(offset, offset + count)
	}
	fun peekch():Char = this.str[offset]
	fun read(count:Int):String {
		val out = this.peek(count)
		this.offset += count
		return out
	}
	fun skipSpaces():StrReader {
		while (this.peek(1).isNullOrBlank()) this.read()
		return this
	}
	fun expect(expected:String):String {
		val result = this.read(expected.length)
		if (result != expected) throw InvalidOperationException("Expecting '$expected' but found '$result' in '$str'")
		return result
	}
	override fun toString() = "StrReader('$str')"

	fun <T> readList(min: Int, readElement: (s:StrReader) -> T?): List<T> {
		var out = arrayListOf<T>()
		while (this.hasMore) {
			out.add(readElement(this) ?: break)
		}
		if (out.size < min) invalidOp("Expected a list of at least $min elements and had ${out.size}")
		return out
	}
}

fun Reader.readUntil(expectedCh:Set<Char>, including:Boolean = false, readDelimiter:Boolean = true):String {
	var out = ""
	while (this.hasMore) {
		val ch = this.peekch()
		if (ch in expectedCh) {
			if (including) out += ch
			if (readDelimiter) this.readch()
			break
		}
		out += ch
		this.readch()
	}
	return out
}

fun Reader.readUntil(vararg expectedCh:Char, including:Boolean = false, readDelimiter:Boolean = true):String = this.readUntil(expectedCh.toSet(), including)

fun Reader.read(count:Int):String {
	val out = CharArray(count)
	this.read(out)
	return String(out)
}

fun Reader.peek(count:Int):String {
	val out = CharArray(count)
	this.mark(count)
	this.read(out)
	this.reset()
	return String(out)
}

fun Reader.peekch():Char {
	this.mark(1)
	val result = this.readch()
	this.reset()
	return result
}

fun Reader.expect(expected:String):String {
	val result = this.read(expected.length)
	if (result != expected) throw InvalidOperationException()
	return result
}

val Reader.hasMore:Boolean get() = !this.eof
val Reader.eof:Boolean get() {
	this.mark(1)
	val result = this.read()
	this.reset()
	return result < 0
}

fun Reader.readch():Char {
	val value = this.read()
	if (value < 0) throw RuntimeException("End of stream")
	return value.toChar()
}

fun <T : Reader> T.skipSpaces():T {
	while (this.peek(1).isNullOrBlank()) {
		this.read()
	}
	return this
}






fun StrReader.readUntil(expectedCh:Set<Char>, including:Boolean = false, readDelimiter:Boolean = true):String {
	var out = ""
	while (this.hasMore) {
		val ch = this.peekch()
		if (ch in expectedCh) {
			if (including) out += ch
			if (readDelimiter) this.readch()
			break
		}
		out += ch
		this.readch()
	}
	return out
}

fun StrReader.readUntil(vararg expectedCh:Char, including:Boolean = false, readDelimiter:Boolean = true):String = this.readUntil(expectedCh.toSet(), including)
