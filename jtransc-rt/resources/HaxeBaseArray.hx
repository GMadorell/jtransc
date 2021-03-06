class HaxeBaseArray extends java_.lang.Object_ {
    public var length:Int = 0;
	public var desc:String;

	override public function toString__Ljava_lang_String_():java_.lang.String_ {
	    return HaxeNatives.str("HaxeBaseArray");
	}

	#if debug
	private function checkBounds(index:Int) {
		if (index < 0 || index >= length) {
			var e = new java_.lang.ArrayIndexOutOfBoundsException_();
			e._init__I_V(index);
			throw e;
		}
	}
	#else
	inline private function checkBounds(index:Int) {
	}
	#end

	public function getDynamic(index:Int):Dynamic {
		checkBounds(index);
	    return null;
	}

	public function setDynamic(index:Int, value:Dynamic) {
		checkBounds(index);
	}

    public function sort(from:Int, to:Int) {
        if (from != 0 || to != length) throw "HaxeArray.sort not implementeed for ranges";
        //data.sort();
        //throw "HaxeArray.sort not implementeed";
    }
}