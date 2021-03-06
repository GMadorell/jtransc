import haxe.io.UInt8Array;
import haxe.io.Bytes;

class HaxeByteArray extends HaxeBaseArray {
    public var data:UInt8Array = null;

    public function new(length:Int) {
        super();
        this.data = new UInt8Array(length);
        this.length = length;
        this.desc = "[B";
    }

    static public function fromArray(items:Array<Dynamic>) {
        var out = new HaxeByteArray(items.length);
        for (n in 0 ... items.length) out.set(n, items[n]);
        return out;
    }

    static public function fromUInt8Array(items:UInt8Array) {
        var out = new HaxeByteArray(items.length);
        for (n in 0 ... items.length) out.set(n, items[n]);
        return out;
    }

    static public function fromBytes(bytes:Bytes) {
        var out = new HaxeByteArray(bytes.length);
        var bytesData = bytes.getData();
        for (n in 0 ... bytes.length) out.set(n, Bytes.fastGet(bytesData, n));
        return out;
    }

    inline public function get(index:Int):Int {
		checkBounds(index);
        return (this.data[index] << 24) >> 24;
    }

    inline public function set(index:Int, value:Int):Void {
		checkBounds(index);
        this.data[index] = value;
    }

	override public function getDynamic(index:Int):Dynamic {
	    return get(index);
	}

	override public function setDynamic(index:Int, value:Dynamic) {
	    set(index, value);
	}

    public function join(separator:String) {
        var out = '';
        for (n in 0 ... length) {
            if (n != 0) out += separator;
            out += get(n);
        }
        return out;
    }

    public override function clone__Ljava_lang_Object_():java_.lang.Object_ {
        var out = new HaxeByteArray(length);
        copy(this, out, 0, 0, length);
        return out;
    }

    static public function copy(from:HaxeByteArray, to:HaxeByteArray, fromPos:Int, toPos:Int, length:Int) {
        for (n in 0 ... length) to.set(toPos + n, from.get(fromPos + n));
    }
}