package util;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Map;

public class ParseJson {
    //扩展的触发动作 使用 Map<Object,Object> 结构存储
    private static Map<Object,Object> ActionMap;

    public static void main(String[] args) throws FileNotFoundException {
        FileReader reader = new FileReader(args[0]);
        Object Obj = JSONValue.parse(reader);
        ActionMap = (JSONObject) Obj;
    }
}
