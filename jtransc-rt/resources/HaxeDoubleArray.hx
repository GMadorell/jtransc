import haxe.io.Float64Array;

class HaxeDoubleArray extends HaxeBaseArray {
    public var data:Float64Array = null;

    public function new(length:Int) {
        super();
        this.data = new Float64Array(length);
        this.length = length;
        this.desc = "[D";
    }

    static public function fromArray(items:Array<Dynamic>) {
        var out = new HaxeDoubleArray(items.length);
        for (n in 0 ... items.length) out.set(n, items[n]);
        return out;
    }

    inline public function get(index:Int):Float {
		checkBounds(index);
        return this.data[index];
    }

    inline public function set(index:Int, value:Float):Void {
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
        var out = new HaxeDoubleArray(length);
        copy(this, out, 0, 0, length);
        return out;
    }

    static public function copy(from:HaxeDoubleArray, to:HaxeDoubleArray, fromPos:Int, toPos:Int, length:Int) {
		var _from:Float64Array = from.data;
		var _to:Float64Array = to.data;
        for (n in 0 ... length) _to[toPos + n] = _from[fromPos + n];
    }
}