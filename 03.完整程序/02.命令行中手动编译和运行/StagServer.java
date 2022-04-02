import com.alexmerz.graphviz.ParseException;
import com.alexmerz.graphviz.Parser;
import com.alexmerz.graphviz.objects.Edge;
import com.alexmerz.graphviz.objects.Graph;
import com.alexmerz.graphviz.objects.Node;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.*;
import java.net.*;
import java.util.*;


public class StagServer {
    //游戏地图 使用由Map结构组合而成的数据结构存储
    private static Map<String, Object> GameMap;
    //有向边
    private static Map<String, List<String>> Edges;
    //地图的开始点
    private static String StartLocation;
    //地图的所有不重复的地点
    private static List<String> AllLocation;
    //存储所有玩家数据的数据结构 包含玩家拾取的道具、玩家健康值、玩家当前位置
    private static Map<String, Object> UserMap;
    //存储扩展的触发动作
    private static Map<Object, Object> ActionMap;
    //内置的触发动作
    private static final List<String> BuiltinTriggers = Arrays.asList("inventory", "inv", "get", "drop", "goto", "look", "health");
    //扩展的触发动作
    private static List<String> ExtendedTriggers = new LinkedList<>();

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java StagServer <entity-file> <action-file>");
        } else {
            LoadingGame(args[0], args[1]);
        }
    }

    //入口
    private static void LoadingGame(String entityFilename, String actionFilename) {

        //加载游戏地图和扩展动作
        LoadingMap(entityFilename, actionFilename);

        //处理连接
        try {
            ServerSocket ss = new ServerSocket(8888);
            System.out.println("Server Listening");
            while (true) {
                acceptNextConnection(ss);
            }
        } catch (IOException ioe) {
            System.err.println(ioe);
        }
    }

    //从文件加载地图和扩展动作
    @SuppressWarnings("unchecked")
    private static void LoadingMap(String entityFilename, String actionFilename) {
        //分别读取json和dot文件
        FileReader dot_reader = null;
        try {
            dot_reader = new FileReader(entityFilename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        FileReader json_reader = null;
        try {
            json_reader = new FileReader(actionFilename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        //处理 actionFile
        Object Obj = JSONValue.parse(json_reader);
        ActionMap = (JSONObject) Obj;

        //处理 jsonFile
        Parser dot_parser = new Parser();
        try {
            dot_parser.parse(dot_reader);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        //将json文件内容加载到Map
        ArrayList<Graph> graphs = dot_parser.getGraphs();
        ArrayList<Graph> subGraphs = graphs.get(0).getSubgraphs();

        //将游戏地图由文件加载到Map<String, Object>
        GameMap = GetMapFromGraph(subGraphs);

        //获取有向边加载到 Map<String, List<String>>
        Edges = GetEdges(subGraphs);

        //获取开始点
        StartLocation = GetStartLocation(subGraphs);

        //全局玩家数据结构
        UserMap = new HashMap<>();

        //扩展触发动作
        ExtendedTriggers = GetExtendedTriggers();

        //所有地点
        AllLocation = GetAllLocation(subGraphs);
    }

    //单次连接
    private static void acceptNextConnection(ServerSocket ss) {
        try {
            // Next line will block until a connection is received
            Socket socket = ss.accept();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            processNextCommand(in, out);
            out.close();
            in.close();
            socket.close();
        } catch (IOException ioe) {
            System.err.println(ioe);
        }
    }

    //接收与回应处理
    private static void processNextCommand(BufferedReader in, BufferedWriter out) throws IOException {
        String line = in.readLine();
        String result = CommandController(line);
        out.write(result);
    }

    //客户端指令解析器
    private static String CommandController(String in) {
        in = in.trim();
        String result = "";
        String action = "";
        String entity = "";
        String[] strArr = in.split(":");
        String[] strArr2 = new String[0];
        String player = strArr[0];
        if (strArr.length == 1) {
            return result;
        }
        if (strArr.length == 2) {
            strArr2 = (strArr[1].trim()).replaceAll("\\s+", " ").split(" ");
            action = strArr2[0];
            if (strArr2.length == 2) {
                entity = strArr2[1];
            }
        }

        //判断指令是否有效
        if (BuiltinTriggers.contains(action)) {
            //初始化玩家 函数内会自动判断该玩家有没有被初始化 不必担心 所以可多次初始化
            InitPlayer(player);

            //解析
            switch (action) {
                case "get":
                    result = PlayerGetArtefact(player, entity);
                    break;
                case "look":
                    result = PlayerLook(player);
                    break;
                case "drop":
                    result = PlayerDropArtefact(player, entity);
                    break;
                case "inventory":
                case "inv":
                    result = PlayerInventory(player);
                    break;
                case "goto":
                    PlayerGotoNextLocation(player, entity);
                    result = PlayerLook(player);
                    break;
                case "health":
                    OperatePlayerHealth("subtraction", 1, player);
                    result = PlayerLook(player);
                    break;
            }
        } else if (ExtendedTriggers.contains(action)) {
            //初始化玩家 函数内会自动判断该玩家有没有被初始化 不必担心 所以可多次初始化
            InitPlayer(player);

            //判断用户输入的命令与作用对象是否匹配
            boolean flag1, flag2;
            List<String> subjects = GetObjectsByTrigger("subjects", action);
            flag2 = true;
            for (String temp1 : subjects) {
                flag1 = false;
                for (int i = 0; i < strArr2.length; i++) {
                    if (strArr2[i].equals(temp1)) {
                        flag1 = true;
                        break;
                    }
                }
                if (!flag1) {
                    flag2 = false;
                }
            }

            //如果用户输入的命令与作用对象匹配
            if (flag2) {
                //如果作用的对象存在
                if (IsObjectInMap("characters", subjects.get(0))) {

                    //作用的对象在当前地点
                    if (!IsObjectInLocation("characters", subjects.get(0), GetLocationByPlayerName(player))) {
                        return "There is no " + subjects.get(0);
                    }
                }
                if (IsObjectInMap("furniture", subjects.get(0))) {

                    //作用的对象在当前地点
                    if (!IsObjectInLocation("furniture", subjects.get(0), GetLocationByPlayerName(player))) {
                        return "There is no " + subjects.get(0);
                    }
                }
                if (IsObjectInMap("characters", subjects.get(0))) {

                    //作用的对象在当前地点
                    if (!IsObjectInLocation("characters", subjects.get(0), GetLocationByPlayerName(player))) {
                        return "There is no " + subjects.get(0);
                    }
                }

                //获取消耗的对象
                List<String> consumed = GetObjectsByTrigger("consumed", action);

                //如果消耗的对象为空
                if (consumed.isEmpty()) {

                    //什么都不做

                    //如果消耗的对象为道具 道具可能在地图也可能在玩家背包
                } else if (IsObjectInMap("artefacts", consumed.get(0)) || IsArtefactBelongPlayer(player, consumed.get(0))) {
                    //玩家是否有道具
                    if (IsArtefactBelongPlayer(player, consumed.get(0))) {
                        //从玩家背包中删除该道具
                        OperateObjectAboutPlayer("delete", "artefacts", consumed.get(0), "", player);
                    } else {
                        return "Player don't have " + consumed.get(0);
                    }

                    //如果消耗的对象为家具
                } else if (IsObjectInMap("furniture", consumed.get(0))) {

                    //当前地点有次家具
                    if (IsObjectInLocation("furniture", consumed.get(0), GetLocationByPlayerName(player))) {
                        //从当前地点寻找家具并删除
                        OperateObjectAboutLocation("delete", "furniture", consumed.get(0), "", GetLocationByPlayerName(player));
                    } else {
                        return "There is no " + consumed.get(0);
                    }

                } else if ("health".equals(consumed.get(0))) {

                    //将玩家的健康值-1
                    OperatePlayerHealth("subtraction", 1, player);

                } else {
                    return "There is no " + consumed.get(0);
                }

                //获取产生的对象
                List<String> produced = GetObjectsByTrigger("produced", action);

                if (produced.isEmpty()) {

                    //什么都不做

                } else if ("health".equals(produced.get(0))) {

                    //将玩家的健康值+1
                    OperatePlayerHealth("add", 1, player);

                } else if (AllLocation.contains(produced.get(0))) {

                    //将当前地点和该地图关键词设置为双向路线
                    AddEdge(GetLocationByPlayerName(player), produced.get(0));
                    AddEdge(produced.get(0), GetLocationByPlayerName(player));

                } else {

                    //将产生的对象放在当前地图
                    OperateObjectAboutLocation("add", "artefacts", produced.get(0), produced.get(0), GetLocationByPlayerName(player));

                }

                result = GetNarrationByTrigger(action);

            } else {
                result = "Invalid subjects!";
            }
        } else {
            result = "Invalid Command!";
        }

        return result;
    }

    //游戏内置动作 inv 查看玩家背包内容
    private static String PlayerInventory(String playerName) {
        Map<String, String> artefacts_value;
        StringBuilder result = new StringBuilder();
        artefacts_value = GetArtefactsByPlayer(playerName);
        if (artefacts_value != null) {
            for (Map.Entry<String, String> entry : artefacts_value.entrySet()) {
                result.append(entry.getKey()).append(" (").append(entry.getValue()).append(")").append("\n");
            }
        }

        return String.valueOf(result);
    }

    //游戏内置动作 get 拾取一件可以被拾取的物品到指定玩家的背包
    private static String PlayerGetArtefact(String playerName, String artefact) {
        /*
          分为几个阶段操作
          判断玩家是否存在
          判断要拾取的物品是否满足要求：物品是道具而不是人物、家具；物品位于当前位置
          从地图中拾取该物品
          将物品增加到本玩家的背包中
         */

        if (!IsPlayerExisted(playerName)) {
            return "Player does not exist";
        }

        String present_location = GetLocationByPlayerName(playerName);

        if (IsObjectInLocation("artefacts", artefact, present_location)) {
            //获取要拾取道具的名称和描述
            String artefact_description = GetDescriptionByLocationAndArtefact(present_location, artefact);
            //从地图中删除道具
            OperateObjectAboutLocation("delete", "artefacts", artefact, "", present_location);
            //将物品增加到人物背包
            OperateObjectAboutPlayer("add", "artefacts", artefact, artefact_description, playerName);

            return "You picked up " + artefact;
        } else {
            return "The " + artefact + " does not exist in the current location";
        }
    }

    //游戏内置动作 drop 将已有的道具丢弃在当前地图位置
    private static String PlayerDropArtefact(String playerName, String artefact) {
        /*
          玩家丢弃物品分为几个阶段
          一 检查玩家是否存在
          二 检查自己当前有没有此物品
          三 从自己背包中删除该道具
          四 在当前地图中增加该道具
         */
        String present_location;

        if (!IsPlayerExisted(playerName)) {
            return "Player does not exist";
        }

        present_location = GetLocationByPlayerName(playerName);

        if (IsArtefactBelongPlayer(playerName, artefact)) {
            //获取要删除道具的名称和描述
            String artefact_description = GetDescriptionByPlayerAndArtefact(playerName, artefact);
            //从玩家背包删除道具
            OperateObjectAboutPlayer("delete", "artefacts", artefact, "", playerName);
            //向地图中添加道具
            OperateObjectAboutLocation("add", "artefacts", artefact, artefact_description, present_location);

            return "You dropped " + artefact;
        } else {
            return "The " + artefact + " does not exist in the current player";
        }

    }

    //游戏内置动作 goto 切换玩家的当前地点
    private static void PlayerGotoNextLocation(String playerName, String next_location) {
        String present_location = GetLocationByPlayerName(playerName);
        List<String> next_location_list;
        if (!("".equals(present_location))) {
            next_location_list = GetNextLocation(present_location);
            if (next_location_list != null) {
                if (next_location_list.contains(next_location)) {
                    SetPlayerLocation(playerName, next_location);
                }
            }
        }
    }

    //游戏内置动作 look 玩家可以看到当前位置的物品列表
    private static String PlayerLook(String playerName) {
        /*
          对当前所处位置的描述
          可以看到的实体：道具+家具
          在同一位置的玩家
          接下来可以去到哪里
         */
        String location_description;
        StringBuilder artefacts = new StringBuilder();
        StringBuilder furniture = new StringBuilder();
        StringBuilder character = new StringBuilder();
        StringBuilder present_player = new StringBuilder();
        StringBuilder next_location = new StringBuilder();
        int health;

        //获取玩家当前位置
        String present_location = GetLocationByPlayerName(playerName);

        //地点描述
        location_description = GetLocationDescription(present_location);

        //道具
        List<String> artefacts_list = GetObjectsByLocation("artefacts", present_location);
        for (String temp : artefacts_list) {
            artefacts.append(temp).append("\n");
        }

        //家具
        List<String> furniture_list = GetObjectsByLocation("furniture", present_location);
        for (String temp : furniture_list) {
            furniture.append(temp).append("\n");
        }

        //人物 不是玩家
        List<String> characters_list = GetObjectsByLocation("characters", present_location);
        for (String temp : characters_list) {
            character.append(temp).append("\n");
        }

        //健康值
        health = GetPlayerHealth(playerName);

        //当前处于相同点的玩家
        List<String> present_player_list = GetPresentLocationPlayerList(playerName);
        if (present_player_list != null) {
            for (String temp : present_player_list) {
                present_player.append(temp).append("\n");
            }
        }

        //获取下个可去的点
        List<String> next_location_list = GetNextLocation(present_location);
        if (next_location_list != null) {
            for (String temp : next_location_list) {
                next_location.append(temp).append("\n");
            }
        }

        String result;
        result = "You are in " + location_description + ". You can see:" + "\n" +
                artefacts +
                furniture +
                character + "\n" +
                "Your current health value: " + "\n" +
                health + "\n\n" +
                "Players who in the same position with you:" + "\n" +
                present_player + "\n" +
                "You can access from here:" + "\n" +
                next_location;

        return result;
    }

    //获取扩展动作列表
    @SuppressWarnings("unchecked")
    private static List<String> GetExtendedTriggers() {
        List<String> result = new ArrayList<>();
        JSONArray actions = (JSONArray) ActionMap.get("actions");
        Iterator iterator = actions.iterator();
        while (iterator.hasNext()) {
            Map<String, Object> actions_value = (Map<String, Object>) iterator.next();
            List<String> triggers = (ArrayList<String>) actions_value.get("triggers");
            for (String temp : triggers) {
                if (!result.contains(temp)) {
                    result.add(temp);
                }
            }
        }
        return result;
    }

    //通过触发动作 triggers 获取对应的作用对象 subjects 消耗对象 consumed 生成对象 produced
    @SuppressWarnings("unchecked")
    private static List<String> GetObjectsByTrigger(String object_type, String object_value) {
        List<String> objects = new ArrayList<>();
        JSONArray actions = (JSONArray) ActionMap.get("actions");
        Iterator iterator = actions.iterator();
        while (iterator.hasNext()) {
            Map<String, Object> actions_value = (Map<String, Object>) iterator.next();
            List<String> triggers = (ArrayList<String>) actions_value.get("triggers");
            if (triggers.contains(object_value)) {
                objects = (List<String>) actions_value.get(object_type);
                break;
            }
        }
        return objects;
    }

    //通过触发动作 triggers 获取对应的叙述 narration
    @SuppressWarnings("unchecked")
    private static String GetNarrationByTrigger(String trigger) {
        String result = null;
        JSONArray actions = (JSONArray) ActionMap.get("actions");
        Iterator iterator = actions.iterator();
        while (iterator.hasNext()) {
            Map<String, Object> actions_value = (Map<String, Object>) iterator.next();
            List<String> triggers = (ArrayList<String>) actions_value.get("triggers");
            if (triggers.contains(trigger)) {
                result = (String) actions_value.get("narration");
                break;
            }
        }
        return result;
    }

    //通过玩家名称获取当前所处的位置
    @SuppressWarnings("unchecked")
    private static String GetLocationByPlayerName(String playerName) {
        if (!IsPlayerExisted(playerName)) {
            return "";
        }

        Map<String, Object> player_value = (Map<String, Object>) UserMap.get(playerName);

        return (String) player_value.get("location");
    }

    //切换玩家地点 使用前注意判断要设置的位置是否在地图中存在
    @SuppressWarnings("unchecked")
    private static void SetPlayerLocation(String playerName, String location) {
        Map<String, Object> player_value = (Map<String, Object>) UserMap.get(playerName);
        player_value.put("location", location);
    }

    //检查玩家背包中是否有指定道具
    @SuppressWarnings("unchecked")
    private static boolean IsArtefactBelongPlayer(String playerName, String artefact) {

        Map<String, Object> player_value = (Map<String, Object>) UserMap.get(playerName);
        Map<String, String> artefacts_value;

        if (player_value != null) {
            artefacts_value = (Map<String, String>) player_value.get("artefacts");
            if (artefacts_value != null) {
                return artefacts_value.containsKey(artefact);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    //判断 道具 家具 人物 是否在当前地点
    @SuppressWarnings("unchecked")
    private static boolean IsObjectInLocation(String object_type, String object_value, String present_location) {
        //中间值
        Map<String, Object> position_value;
        Map<String, Object> entity_value;
        Map<String, String> temp_artefact_value;

        position_value = (Map<String, Object>) GameMap.get(present_location);

        //如果要找到的地点不在地图 返回空
        if (position_value == null) {
            return false;
        }

        entity_value = (Map<String, Object>) position_value.get("entity");

        temp_artefact_value = (Map<String, String>) entity_value.get(object_type);

        //判断是否存在 artefacts
        if (temp_artefact_value != null) {
            //判断是否存在
            return temp_artefact_value.containsKey(object_value);
        } else {
            return false;
        }
    }

    //向地图中添加或者删除人物 道具 家具
    @SuppressWarnings("unchecked")
    private static void OperateObjectAboutLocation(String operate_type, String object_type, String object_name, String object_description, String present_location) {
        Map<String, Object> position_value;
        Map<String, Object> entity_value;
        Map<String, String> object_value;

        position_value = (Map<String, Object>) GameMap.get(present_location);

        //如果要找到的地点不在地图
        if (position_value == null) return;

        entity_value = (Map<String, Object>) position_value.get("entity");

        //如果该地点没有初始化该类型的实体则现在添加
        if (!entity_value.containsKey(object_type)) {
            entity_value.put(object_type, new HashMap<String, String>());
        }

        object_value = (Map<String, String>) entity_value.get(object_type);

        switch (operate_type) {
            case "delete":
                if (!object_value.isEmpty()) {
                    object_value.remove(object_name);
                }
                break;
            case "add":
                object_value.put(object_name, object_description);
                break;
        }
    }

    //向背包中添加或删除道具
    @SuppressWarnings("unchecked")
    private static void OperateObjectAboutPlayer(String operate_type, String object_type, String object_name, String object_description, String playerName) {
        Map<String, Object> player_value = (Map<String, Object>) UserMap.get(playerName);
        Map<String, String> artefacts_value;

        switch (operate_type) {
            case "delete":
                if (!player_value.isEmpty()) {
                    artefacts_value = (Map<String, String>) player_value.get(object_type);
                    artefacts_value.remove(object_name);
                }
                break;
            case "add":
                artefacts_value = (Map<String, String>) player_value.get(object_type);
                artefacts_value.put(object_name, object_description);
                break;
        }
    }

    //根据玩家名称获取背包内容
    @SuppressWarnings("unchecked")
    private static Map<String, String> GetArtefactsByPlayer(String playerName) {
        Map<String, Object> player_value = (Map<String, Object>) UserMap.get(playerName);
        Map<String, String> artefacts_value = new HashMap<>();

        if (!player_value.isEmpty()) {
            artefacts_value = (Map<String, String>) player_value.get("artefacts");
        }

        if (player_value.isEmpty()) {
            return null;
        } else {
            return artefacts_value;
        }
    }

    //通过地点获取下一个可去的地点 存在有多个可去的地点的情况
    private static List<String> GetNextLocation(String present_location) {
        //声明
        List<String> nextNodeList;

        //在有向边的集合中根据key值查找对应的多个value值
        nextNodeList = Edges.get(present_location);

        return nextNodeList;
    }

    //获取某个地点的玩家列表
    @SuppressWarnings("unchecked")
    private static List<String> GetPresentLocationPlayerList(String playerName) {
        List<String> playerList = new ArrayList<>();
        String present_location = GetLocationByPlayerName(playerName);
        //遍历所有玩家
        if (UserMap != null) {
            for (Map.Entry<String, Object> entry : UserMap.entrySet()) {
                String this_player = entry.getKey();
                if (!playerName.equals(this_player)) {
                    Map<String, Object> player_value = (Map<String, Object>) entry.getValue();
                    String this_location = (String) player_value.get("location");
                    if (present_location.equals(this_location)) {
                        playerList.add(this_player);
                    }
                }
            }
        }
        if (playerList.isEmpty()) {
            return null;
        } else {
            return playerList;
        }
    }

    //获取某个地点的家具furniture 道具artefacts 人物characters列表
    @SuppressWarnings("unchecked")
    private static List<String> GetObjectsByLocation(String object_type, String present_location) {
        //中间值
        Map<String, Object> position_value;
        Map<String, Object> entity_value;
        Map<String, String> artefact_value;
        //返回 描述文字+物品名称的组合
        List<String> resultList = new ArrayList<>();

        position_value = (Map<String, Object>) GameMap.get(present_location);

        //如果要找到的地点不在地图 返回空
        if (position_value == null) {
            return resultList;
        }

        entity_value = (Map<String, Object>) position_value.get("entity");

        artefact_value = (Map<String, String>) entity_value.get(object_type);

        //如果该节点存在artefacts节点
        if (artefact_value != null) {
            for (Map.Entry<String, String> entry : artefact_value.entrySet()) {
                resultList.add(entry.getKey() + " (" + entry.getValue() + ")");
            }
        }

        return resultList;
    }

    //判断物品是否为地图中的家具furniture 道具artefacts 人物characters列表
    @SuppressWarnings("unchecked")
    private static boolean IsObjectInMap(String object_type, String object_value) {
        Map<String, Object> position_value;
        Map<String, Object> entity_value;
        Map<String, String> temp_object_value;

        for (Map.Entry entry1 : GameMap.entrySet()) {

            position_value = (Map<String, Object>) GameMap.get(entry1.getKey());

            //如果要找到的地点不在地图 返回空
            if (position_value == null) {
                return false;
            }

            entity_value = (Map<String, Object>) position_value.get("entity");

            temp_object_value = (Map<String, String>) entity_value.get(object_type);

            //如果该节点存在temp_object_value节点
            if (temp_object_value != null) {
                if (temp_object_value.containsKey(object_value)) {
                    return true;
                }
            }
        }

        return false;
    }

    //获取某个地点的描述信息
    @SuppressWarnings("unchecked")
    private static String GetLocationDescription(String present_location) {

        Map<String, Object> position_value = (Map<String, Object>) GameMap.get(present_location);

        //地点不存在的情况
        if (position_value == null) {
            return "";
        }

        return position_value.get("description").toString();
    }

    //根据地点和道具名称获取道具的描述 此时道具没有被拾取
    @SuppressWarnings("unchecked")
    private static String GetDescriptionByLocationAndArtefact(String present_location, String artefact) {
        Map<String, Object> position_value = (Map<String, Object>) GameMap.get(present_location);
        Map<String, Object> entity_value;
        Map<String, String> artefacts_value;

        if (position_value != null) {
            entity_value = (Map<String, Object>) position_value.get("entity");
            if (entity_value != null) {
                artefacts_value = (Map<String, String>) entity_value.get("artefacts");
                if (artefacts_value != null) {
                    return artefacts_value.get(artefact);
                }
            }
        }
        return "";
    }

    //根据玩家和道具名称获取道具描述 此时道具已经被拾取 在玩家的背包
    @SuppressWarnings("unchecked")
    private static String GetDescriptionByPlayerAndArtefact(String playerName, String artefact) {
        Map<String, Object> player_value = (Map<String, Object>) UserMap.get(playerName);
        Map<String, String> artefacts_value;

        if (player_value != null) {
            artefacts_value = (Map<String, String>) player_value.get("artefacts");
            return artefacts_value.get(artefact);
        } else {
            return "";
        }
    }

    //解析地图 增强理解 程序中没有调用
    private static Map<String, Map<String, String>> GetMapFromGraph_(ArrayList<Graph> subGraphs) {
        //遍历子图列表的每个子图
        for (Graph g : subGraphs) {
            ArrayList<Graph> subGraphs1 = g.getSubgraphs();
            for (Graph g1 : subGraphs1) {
                ArrayList<Node> nodesLoc = g1.getNodes(false);
                Node nLoc = nodesLoc.get(0);
                //每张地图的名称
                System.out.printf("%s\n", g1.getId().getId());
                //森林 小木屋 地窖 河岸 未放置 等地图的地点
                System.out.printf("\t%s -> %s\n", nLoc.getId().getId(), nLoc.getAttribute("description"));
                ArrayList<Graph> subGraphs2 = g1.getSubgraphs();
                for (Graph g2 : subGraphs2) {
                    //道具 artefacts，人物 characters，家具 furniture
                    System.out.printf("\t\t%s\n", g2.getId().getId());
                    ArrayList<Node> nodesEnt = g2.getNodes(false);
                    for (Node nEnt : nodesEnt) {
                        //道具 artefacts，人物 characters，家具 furniture下的具体事务
                        System.out.printf("\t\t\t%s -> %s\n", nEnt.getId().getId(), nEnt.getAttribute("description"));
                    }
                }
            }
        }

        return null;
    }

    //将地图内容内容加载到Map数据结构
    private static Map<String, Object> GetMapFromGraph(ArrayList<Graph> subGraphs) {
        //地点
        Map<String, Object> position = new HashMap<>();
        //每个地点的描述和实体
        Map<String, Object> position_value = new HashMap<>();
        //实体
        Map<String, Object> entity = new HashMap<>();
        //实体内容
        Map<String, Object> entity_value = new HashMap<>();

        //遍历子图列表的每个子图
        for (Graph g : subGraphs) {
            ArrayList<Graph> subGraphs1 = g.getSubgraphs();
            for (Graph g1 : subGraphs1) {
                ArrayList<Node> nodesLoc = g1.getNodes(false);
                Node nLoc = nodesLoc.get(0);

                //每张地图的名称
//                System.out.printf("%s\n", g1.getId().getId());

                //森林 小木屋 地窖 河岸 未放置 等地图的地点
//                System.out.printf("\t%s -> %s\n", nLoc.getId().getId(), nLoc.getAttribute("description"));
                position_value.put("description", new String(nLoc.getAttribute("description")));

                ArrayList<Graph> subGraphs2 = g1.getSubgraphs();
                for (Graph g2 : subGraphs2) {
                    //道具 artefacts，人物 characters，家具 furniture
//                    System.out.printf("\t\t%s\n", g2.getId().getId());

                    ArrayList<Node> nodesEnt = g2.getNodes(false);
                    for (Node nEnt : nodesEnt) {
                        //道具 artefacts，人物 characters，家具 furniture下的具体事物
//                        System.out.printf("\t\t\t%s -> %s\n", nEnt.getId().getId(), nEnt.getAttribute("description"));
                        entity_value.put(new String(nEnt.getId().getId()), new String(nEnt.getAttribute("description")));
                    }

                    entity.put(new String(g2.getId().getId()), new HashMap<>(entity_value));
                    entity_value.clear();
                }
                position_value.put("entity", new HashMap<>(entity));
                entity.clear();

                position.put(new String(nLoc.getId().getId()), new HashMap<>(position_value));
            }
        }

        return position;
    }

    //获取有向图的所有有方向的边
    private static Map<String, List<String>> GetEdges(ArrayList<Graph> subGraphs) {

        //返回的有向边集合
        Map<String, List<String>> edgeList = new HashMap<>();

        List<String> temp;

        //一条边的起始和目的节点
        String temp_source_location;
        String temp_target_location;

        //遍历子图列表的每个子图
        for (Graph g : subGraphs) {

            //获取有向图的边的集合
            ArrayList<Edge> edges = g.getEdges();

            //遍历每个边
            for (Edge e : edges) {

                //获取每条边的起始和结束节点
                temp_source_location = e.getSource().getNode().getId().getId();
                temp_target_location = e.getTarget().getNode().getId().getId();

                //将有向边存储在Map中且不允许有重复
                if (edgeList.containsKey(temp_source_location)) {
                    temp = edgeList.get(temp_source_location);
                    if (!temp.contains(temp_target_location)) {
                        temp.add(new String(temp_target_location));
                        edgeList.put(new String(temp_source_location), new ArrayList<>(temp));
                    }
                } else {
                    temp = new ArrayList<>();
                    temp.add(new String(temp_target_location));
                    edgeList.put(new String(temp_source_location), new ArrayList<>(temp));
                }
            }
        }

        return edgeList;
    }

    //获取地图的所有不重复的地点
    private static List<String> GetAllLocation(ArrayList<Graph> subGraphs) {

        List<String> result = new ArrayList<>();

        //一条边的起始和目的节点
        String temp_source_location;
        String temp_target_location;

        //遍历子图列表的每个子图
        for (Graph g : subGraphs) {

            //获取有向图的边的集合
            ArrayList<Edge> edges = g.getEdges();

            //遍历每个边
            for (Edge e : edges) {

                //获取每条边的起始和结束节点
                temp_source_location = e.getSource().getNode().getId().getId();
                temp_target_location = e.getTarget().getNode().getId().getId();

                if (!result.contains(temp_source_location)) {
                    result.add(new String(temp_source_location));
                }
                if (!result.contains(temp_target_location)) {
                    result.add(new String(temp_target_location));
                }
            }
        }

        return result;
    }

    //获取地图的开始地点
    private static String GetStartLocation(ArrayList<Graph> subGraphs) {
        //遍历子图列表的每个子图
        for (Graph g : subGraphs) {
            ArrayList<Graph> subGraphs1 = g.getSubgraphs();
            for (Graph g1 : subGraphs1) {
                ArrayList<Node> nodesLoc = g1.getNodes(false);
                Node nLoc = nodesLoc.get(0);
                return nLoc.getId().getId();
            }
        }
        return null;
    }

    //向地图中添加新的路线 前提是在地图存在的范围内
    private static void AddEdge(String source_location, String target_location) {
        List<String> temp_target_location_list = Edges.get(source_location);
        if (Edges.containsKey(source_location)) {
            if (!temp_target_location_list.contains(target_location)) {
                temp_target_location_list.add(target_location);
            }
        } else {
            temp_target_location_list.add(target_location);
            Edges.put(source_location, temp_target_location_list);
        }
    }

    //获取玩家的健康值
    @SuppressWarnings("unchecked")
    private static Integer GetPlayerHealth(String playerName) {
        Map<String, Object> player_value = (Map<String, Object>) UserMap.get(playerName);
        return (Integer) player_value.get("health");
    }

    //设置玩家的健康值增加或者减少指定的值
    @SuppressWarnings("unchecked")
    private static void OperatePlayerHealth(String operate_type, int value, String playerName) {
        Map<String, Object> player_value = (Map<String, Object>) UserMap.get(playerName);
        int health = (int) player_value.get("health");
        switch (operate_type) {
            case "add": {
                if (GetPlayerHealth(playerName) > 0) {
                    player_value.put("health", health + value);
                }
                if (GetPlayerHealth(playerName) == 0 || GetPlayerHealth(playerName) < 0) {
                    PlayerGameOver(playerName);
                }
            }
            break;
            case "subtraction": {
                if (GetPlayerHealth(playerName) > 0) {
                    player_value.put("health", health - value);
                }
                if (GetPlayerHealth(playerName) == 0 || GetPlayerHealth(playerName) < 0) {
                    PlayerGameOver(playerName);
                }
            }
            break;
        }
    }

    //设置玩家的健康值为指定值
    @SuppressWarnings("unchecked")
    private static void SetPlayerHealth(String playerName, int health) {
        Map<String, Object> player_value = (Map<String, Object>) UserMap.get(playerName);
        player_value.put("health", health);
    }

    //玩家由于健康值掉到0 被迫回到初始位置 并且背包中捡到的道具被丢弃到当前的位置
    private static void PlayerGameOver(String playerName) {
        //获取玩家背包道具
        Map<String, String> artefacts_value = GetArtefactsByPlayer(playerName);
        Map<String, String> temp;
        String present_location = GetLocationByPlayerName(playerName);
        //丢弃玩家背包中的道具到当前的位置
        if (!artefacts_value.isEmpty()) {
            temp = new HashMap<>(artefacts_value);
            for (Map.Entry entry : temp.entrySet()) {
                OperateObjectAboutPlayer("delete", "artefacts", (String) entry.getKey(), "", playerName);
                OperateObjectAboutLocation("add", "artefacts", (String) entry.getKey(), (String) entry.getValue(), present_location);
            }
        }
        //将玩家当前的位置更改到初始位置
        SetPlayerLocation(playerName, StartLocation);
        //将健康值设置为初始值3
        SetPlayerHealth(playerName, 3);
    }

    //判断玩家是否已经存在
    private static boolean IsPlayerExisted(String playerName) {
        return UserMap.containsKey(playerName);
    }

    //初始化玩家
    private static void InitPlayer(String playerName) {
        //初始化用户之前要判断该用户是否已经存在
        if (IsPlayerExisted(playerName)) return;

        //玩家的道具
        Map<String, String> artefacts_value = new HashMap<>();
        //玩家
        Map<String, Object> player_value = new HashMap<>();

        //道具
        player_value.put("artefacts", new HashMap<String, Object>(artefacts_value));
        //健康值
        player_value.put("health", 3);
        //将开始位置设置为当前位置
        player_value.put("location", new String(StartLocation));

        //将初始化后的玩家加入到全局玩家列表
        UserMap.put(new String(playerName), new HashMap<>(player_value));
    }

}