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

package java.util;

import java.io.IOException;
import java.io.NotSerializableException;

public class InvalidPropertiesFormatException extends IOException {
	public InvalidPropertiesFormatException(Throwable cause) {
		super(cause == null ? null : cause.toString());
		this.initCause(cause);
	}

	public InvalidPropertiesFormatException(String message) {
		super(message);
	}

	private void writeObject(java.io.ObjectOutputStream out) throws NotSerializableException {
		throw new NotSerializableException("Not serializable.");
	}

	private void readObject(java.io.ObjectInputStream in) throws NotSerializableException {
		throw new NotSerializableException("Not serializable.");
	}
}