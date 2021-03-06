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

import jtransc.annotation.haxe.HaxeAddMembers;
import jtransc.annotation.haxe.HaxeMethodBody;
import jtransc.internal.GenericListIterator;

@HaxeAddMembers("var _data:Array<Dynamic> = [];")
public class ArrayList<E> extends AbstractList<E> implements List<E>, RandomAccess, Cloneable, java.io.Serializable {
	public ArrayList(int initialCapacity) {
	}

	public ArrayList() {
	}

	public ArrayList(Collection<? extends E> c) {
		addAll(c);
	}

	public void trimToSize() {
	}

	public void ensureCapacity(int minCapacity) {
	}

	@HaxeMethodBody("return _data.length;")
	native public int size();

	@HaxeMethodBody("return _data[p0];")
	native private E _get(int index);

	@HaxeMethodBody("_data[p0] = p1;")
	native private void _set(int index, E element);

	@HaxeMethodBody("_data = _data.slice(0, p0);")
	native private void _setLength(int length);

	@HaxeMethodBody("_data.push(p0);")
	native private void _add(E element);

	@HaxeMethodBody("_data.insert(p0, p1);")
	native private void _insert(int index, E element);

	@HaxeMethodBody("_data = _data.slice(0, p0).concat(p1.toArray()).concat(_data.slice(p0));")
	native private void _insert(int index, Object[] elements);

	@HaxeMethodBody("_data = _data.slice(0, p0).concat(_data.slice(p0));")
	native private void _remove(int from, int to);

	@HaxeMethodBody("_data.splice(p0, 1);")
	native private void _remove(int index);

	@HaxeMethodBody("_data = [];")
	native private void _clear();

	@HaxeMethodBody("p0._data = p1._data.slice(0);")
	native static private void _copy(ArrayList<?> dst, ArrayList<?> src);

	public boolean isEmpty() {
		return size() == 0;
	}

	public boolean contains(Object o) {
		return indexOf(o) >= 0;
	}

	public int indexOf(Object o) {
		int len = size();
		for (int i = 0; i < len; i++) if (Objects.equals(o, _get(i))) return i;
		return -1;
	}

	public int lastIndexOf(Object o) {
		int len = size();
		for (int i = len - 1; i >= 0; i--) if (Objects.equals(o, _get(i))) return i;
		return -1;
	}

	public Object clone() {
		try {
			ArrayList<?> v = (ArrayList<?>) super.clone();
			_copy(v, this);
			v.modCount = 0;
			return v;
		} catch (CloneNotSupportedException e) {
			throw new InternalError(e);
		}
	}

	public Object[] toArray() {
		return toArray(new Object[size()]);
	}

	@SuppressWarnings("unchecked")
	public <T> T[] toArray(T[] a) {
		int len = size();
		if (a.length < len) a = (T[]) Arrays.copyOf(new Object[0], len, a.getClass());
		for (int n = 0; n < len; n++) a[n] = (T) _get(n);
		return a;
	}

	public E get(int index) {
		rangeCheck(index);
		return _get(index);
	}

	public E set(int index, E element) {
		rangeCheck(index);
		E oldValue = _get(index);
		_set(index, element);
		return oldValue;
	}

	public boolean add(E e) {
		_add(e);
		return true;
	}

	public void add(int index, E element) {
		rangeCheckForAdd(index);
		_insert(index, element);
	}

	public E remove(int index) {
		rangeCheck(index);

		modCount++;
		E oldValue = _get(index);
		_remove(index);

		return oldValue;
	}

	public boolean remove(Object o) {
		int len = size();
		for (int index = 0; index < len; index++) {
			if (Objects.equals(o, _get(index))) {
				_remove(index);
				return true;
			}
		}
		return false;
	}

	public void clear() {
		modCount++;
		_clear();
	}

	public boolean addAll(Collection<? extends E> c) {
		for (E e : c) add(e);
		return c.size() != 0;
	}

	public boolean addAll(int index, Collection<? extends E> c) {
		rangeCheckForAdd(index);

		_insert(index, c.toArray());

		if (c.size() != 0) modCount++;
		return c.size() != 0;
	}

	protected void removeRange(int fromIndex, int toIndex) {
		modCount++;
		_remove(fromIndex, toIndex);
	}

	private void rangeCheck(int index) {
		if (index >= size()) throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
	}

	private void rangeCheckForAdd(int index) {
		if (index > size() || index < 0) throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
	}

	private String outOfBoundsMsg(int index) {
		return "Index: " + index + ", Size: " + size();
	}

	public boolean removeAll(Collection<?> c) {
		Objects.requireNonNull(c);
		return batchRemove(c, false);
	}

	public boolean retainAll(Collection<?> c) {
		Objects.requireNonNull(c);
		return batchRemove(c, true);
	}

	private boolean batchRemove(Collection<?> c, boolean complement) {
		int r = 0, w = 0;
		for (; r < size(); r++) if (c.contains(_get(r)) == complement) _set(w++, _get(r));
		_setLength(w);
		return r != w;
	}

	public ListIterator<E> listIterator(int index) {
		if (index < 0 || index > size()) throw new IndexOutOfBoundsException("Index: " + index);
		return new GenericListIterator(this, index);
	}

	public ListIterator<E> listIterator() {
		return listIterator(0);
	}

	public Iterator<E> iterator() {
		return listIterator();
	}

	public List<E> subList(int fromIndex, int toIndex) {
		return super.subList(fromIndex, toIndex);
	}
}
