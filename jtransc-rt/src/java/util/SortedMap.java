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

public interface SortedMap<K, V> extends Map<K, V> {
	Comparator<? super K> comparator();

	SortedMap<K, V> subMap(K fromKey, K toKey);

	SortedMap<K, V> headMap(K toKey);

	SortedMap<K, V> tailMap(K fromKey);

	K firstKey();

	K lastKey();

	Set<K> keySet();

	Collection<V> values();

	Set<Entry<K, V>> entrySet();
}
