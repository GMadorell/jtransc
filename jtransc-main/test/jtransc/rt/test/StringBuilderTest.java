package jtransc.rt.test;

public class StringBuilderTest {
	static public void main(String[] args) {
		StringBuilder sb = new StringBuilder("hello");
		sb.append(1.0);
		sb.append(true);
		sb.append(false);
		sb.insert(1, true);
		sb.delete(1, 3);
		sb.append(true);
		System.out.println(sb.indexOf("true"));
		System.out.println(sb.lastIndexOf("true"));
		System.out.println(sb.charAt(7));
		System.out.println(sb);
		sb.replace(5, 5, "test");
		sb.replace(5, 8, "test");
		sb.append("abcdefghijklmn");
		sb.insert(10, "lol");
		sb.reverse();
		System.out.println(sb);
		sb.insert(1, true);
		sb.insert(2, (byte)-77);
		sb.insert(8, 'Z');
		sb.insert(3, (short)-77);
		sb.insert(4, (int)-77);
		sb.insert(5, (long)-77);
		sb.insert(6, (double)-77);
		sb.insert(7, (float)-77);
		sb.insert(3, new char[] { 'a', 'b', 'c' });
		sb.insert(3, new char[] { 'a', 'b', 'c' }, 1, 1);
		sb.append((byte)-77);
		sb.append('a');
		sb.append((short)-77);
		sb.append((int)-77);
		sb.append((long)-77);
		sb.append((double)-77);
		sb.append((float)-77);
		sb.append(new char[] { 'a', 'b', 'c' });
		sb.append(new char[] { 'a', 'b', 'c' }, 1, 1);
		System.out.println(sb);
		System.out.println(sb.substring(3));
		System.out.println(sb.substring(3, 10));
	}
}
