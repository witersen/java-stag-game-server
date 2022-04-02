package util;

import com.alexmerz.graphviz.ParseException;
import com.alexmerz.graphviz.Parser;
import com.alexmerz.graphviz.objects.Edge;
import com.alexmerz.graphviz.objects.Graph;
import com.alexmerz.graphviz.objects.Node;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;

public class ParseDot {

    //游戏地图 使用由Map结构组合而成的数据结构存储
    private static Map<String, Object> GameMap;
    //有向边
    private static Map<String, List<String>> Edges;
    //地图的开始点
    private static String StartLocation;
    //存储所有玩家数据的数据结构 包含玩家拾取的道具、玩家健康值、玩家当前位置
    private static Map<String, Object> UserMap;

    public static void main(String[] args) throws FileNotFoundException, ParseException {
        Parser parser = new Parser();
        FileReader reader = new FileReader(args[0]);
        parser.parse(reader);
        ArrayList<Graph> graphs = parser.getGraphs();
        ArrayList<Graph> subGraphs = graphs.get(0).getSubgraphs();
        //获取游戏地图加载到 Map<String, Object>
        GameMap = GetMapFromGraph(subGraphs);
        //获取有向边加载到 Map<String, List<String>>
        Edges = GetEdges(subGraphs);
        //获取开始点
        StartLocation = GetStartLocation(subGraphs);
        //全局玩家数据结构
        UserMap = new HashMap<String, Object>();
        //初始化玩家
        InitPlayer("blue");

        System.out.println(Edges.toString());
        System.out.println(GetNextNodeListByLocation("cabin"));
    }

    /**
     * 查看玩家背包内容
     *
     * @param playerName
     * @return
     */
    public static String PlayerInventory(String playerName) {
        return ListArtefactFromPlayer(playerName);
    }

    /**
     * 游戏内置动作 get 拾取一件可以被拾取的物品到指定玩家的背包
     *
     * @param playerName
     * @param artefact
     */
    public static void PlayerGetArtefact(String playerName, String artefact) {
        /**
         * 分为几个阶段操作
         * 判断玩家是否存在
         * 判断要拾取的物品是否满足要求：物品是道具而不是人物、家具；物品位于当前位置
         * 从地图中拾取该物品
         * 将物品增加到本玩家的背包中
         */
        String present_location;

        if (!IsPlayerExisted(playerName)) {
            return;
        }

        present_location = GetPresentLocationByPlayerName(playerName);

        if (IsArtefactInLocation(artefact, present_location)) {
            //获取要拾取道具的名称和描述
            String artefact_description = GetDescriptionByLocationAndArtefact(present_location, artefact);
            //从地图中拾取
            PickArtefactFromLocation(artefact, present_location);
            //将物品增加到人物背包
            AddArtefactToPlayer(playerName, artefact, artefact_description);
        }
    }

    /**
     * 游戏内置动作 drop 将已有的道具丢弃在当前地图位置
     *
     * @param playerName
     * @param artefact
     */
    public static void PlayerDropArtefact(String playerName, String artefact) {
        /**
         * 玩家丢弃物品分为几个阶段
         * 一 检查玩家是否存在
         * 二 检查自己当前有没有此物品
         * 三 从自己背包中删除该道具
         * 四 在当前地图中增加该道具
         */
        String present_location;

        if (!IsPlayerExisted(playerName)) {
            return;
        }

        present_location = GetPresentLocationByPlayerName(playerName);

        if (IsArtefactBelongPlayer(playerName, artefact)) {
            //获取要删除道具的名称和描述
            String artefact_description = GetDescriptionByPlayerAndArtefact(playerName, artefact);
            //从玩家背包删除道具
            RemoveArtefactFromPlayer(playerName, artefact);
            //将道具丢弃到当前地图位置
            DropArtefactToLocation(artefact, present_location, artefact_description);
        }

    }

    /**
     * 游戏内置动作 goto 切换玩家的当前地点
     *
     * @param playerName
     * @param next_location
     * @return
     */
    public static boolean PlayerGotoNextLocation(String playerName, String next_location) {
        String present_location = GetPresentLocationByPlayerName(playerName);
        List<String> next_location_list;
        Map<String, Object> player_value;
        if (!("".equals(present_location))) {
            next_location_list = GetNextNodeListByLocation(present_location);
            if (next_location_list.contains(next_location)) {
                player_value = (Map<String, Object>) UserMap.get(playerName);
                player_value.put("location", next_location);
            }
        }

        return false;
    }

    /**
     * 游戏内置动作 look 玩家可以看到当前位置的物品列表
     *
     * @return
     */
    public static String PlayerLook(String playerName) {
        /**
         * 对当前所处位置的描述
         * 可以看到的实体：道具+家具
         * 在同一位置的玩家
         * 接下来可以去到哪里
         */
        String location_description = "";
        String artefacts = "";
        String furniture = "";
        String character = "";
        String next_location = "";

        //获取玩家当前位置
        String present_location = GetPresentLocationByPlayerName(playerName);

        //地点描述
        location_description = GetDescriptionByLocation(present_location);

        //道具
        List<String> artefacts_list = GetArtefactsByLocation(present_location);
        for (String temp : artefacts_list) {
            artefacts += temp + "\n";
        }

        //家具
        List<String> furniture_list = GetFurnitureByLocation(present_location);
        for (String temp : furniture_list) {
            furniture += temp + "\n";
        }

        //人物 不是玩家
        List<String> characters_list = GetCharactersByLocation(present_location);
        for (String temp : characters_list) {
            character += temp + "\n";
        }

        //获取下个可去的点
        List<String> next_location_list = GetNextNodeListByLocation(present_location);
        for (String temp : next_location_list) {
            next_location += temp + " ";
        }

        return "You are in " + location_description + ". You can see: \n" + artefacts + furniture + character + "You can access from here: \n" + next_location;
    }

    /**
     * 通过玩家名称获取当前所处的位置
     *
     * @param playerName
     * @return
     */
    public static String GetPresentLocationByPlayerName(String playerName) {
        if (!IsPlayerExisted(playerName)) {
            return "";
        }

        Map<String, Object> player_value = (Map<String, Object>) UserMap.get(playerName);
        String location = (String) player_value.get("location");

        return location;
    }

    /**
     * 检查玩家背包中是否有指定道具
     *
     * @param playerName
     * @param artefact
     * @return
     */
    public static boolean IsArtefactBelongPlayer(String playerName, String artefact) {

        Map<String, Object> player_value = (Map<String, Object>) UserMap.get(playerName);
        Map<String, String> artefacts_value;

        if (player_value != null) {
            artefacts_value = (Map<String, String>) player_value.get("artefacts");
            if (artefacts_value != null) {
                if (artefacts_value.containsKey(artefact)) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * 判断某个物品是否确实存在当前的地点 因为可能通过look方式看到该物品存在 但是下一秒可能被其他玩家拾取了
     * 同时如果要拾取的物品不是道具 而是 人物 家具等也会返回false
     *
     * @param artefact
     * @param present_location
     * @return
     */
    public static boolean IsArtefactInLocation(String artefact, String present_location) {
        //中间值
        Map<String, Object> position_value = null;
        Map<String, Object> entity_value = null;
        Map<String, String> artefact_value = null;

        position_value = (Map<String, Object>) GameMap.get(present_location);

        //如果要找到的地点不在地图 返回空
        if (position_value == null) {
            return false;
        }

        entity_value = (Map<String, Object>) position_value.get("entity");

        artefact_value = (Map<String, String>) entity_value.get("artefacts");

        //判断是否存在 artefacts
        if (artefact_value != null) {
            //判断是否存在
            if (artefact_value.containsKey(artefact)) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * 从地图中拾取道具
     *
     * @param artefact
     * @param present_location
     */
    public static void PickArtefactFromLocation(String artefact, String present_location) {
        //中间值
        Map<String, Object> position_value = null;
        Map<String, Object> entity_value = null;
        Map<String, String> artefact_value = null;

        position_value = (Map<String, Object>) GameMap.get(present_location);

        //如果要找到的地点不在地图
        if (position_value == null) {
            return;
        }

        entity_value = (Map<String, Object>) position_value.get("entity");

        artefact_value = (Map<String, String>) entity_value.get("artefacts");

        //判断是否存在 artefacts
        if (artefact_value != null) {
            //判断是否存在
            if (artefact_value.containsKey(artefact)) {
                artefact_value.remove(artefact);
            }
        }
    }

    /**
     * 将道具放入当前地图位置
     *
     * @param artefact
     * @param present_location
     * @param artefact_description
     */
    public static void DropArtefactToLocation(String artefact, String present_location, String artefact_description) {
        //中间值
        Map<String, Object> position_value = null;
        Map<String, Object> entity_value = null;
        Map<String, String> artefact_value = null;

        position_value = (Map<String, Object>) GameMap.get(present_location);

        //如果要找到的地点不在地图 返回空
        if (position_value == null) {
            return;
        }

        entity_value = (Map<String, Object>) position_value.get("entity");

        artefact_value = (Map<String, String>) entity_value.get("artefacts");

        //判断是否存在 artefacts
        if (artefact_value != null) {
            //判断是否存在
            if (!artefact_value.containsKey(artefact)) {
                artefact_value.put(artefact, artefact_description);
            }
        }
    }

    /**
     * 将道具加入玩家的背包
     *
     * @param playerName
     * @param artefact
     */
    public static void AddArtefactToPlayer(String playerName, String artefact, String artefact_description) {
        Map<String, Object> player_value = (Map<String, Object>) UserMap.get(playerName);
        Map<String, String> artefacts_value;

        if (player_value != null) {
            artefacts_value = (Map<String, String>) player_value.get("artefacts");
            artefacts_value.put(artefact, artefact_description);
        }
    }

    /**
     * 从玩家背包删除道具
     *
     * @param playerName
     * @param artefact
     */
    public static void RemoveArtefactFromPlayer(String playerName, String artefact) {
        Map<String, Object> player_value = (Map<String, Object>) UserMap.get(playerName);
        Map<String, String> artefacts_value;

        if (player_value != null) {
            artefacts_value = (Map<String, String>) player_value.get("artefacts");
            artefacts_value.remove(artefact);
        }
    }

    /**
     * 根据玩家名称获取背包内容
     *
     * @param playerName
     * @return
     */
    public static String ListArtefactFromPlayer(String playerName) {
        Map<String, Object> player_value = (Map<String, Object>) UserMap.get(playerName);
        Map<String, String> artefacts_value;
        String result = "";

        if (player_value != null) {
            artefacts_value = (Map<String, String>) player_value.get("artefacts");
            if (artefacts_value != null) {
                for (Map.Entry<String, String> entry : artefacts_value.entrySet()) {
                    result += entry.getKey() + " (" + entry.getValue() + ")";
                }
            }
        }

        return result;
    }

    /**
     * 通过地点获取下一个可去的地点 存在有多个可去的地点的情况
     *
     * @param present_location
     * @return
     */
    public static List<String> GetNextNodeListByLocation(String present_location) {
        //声明
        List<String> nextNodeList = null;

        //在有向边的集合中根据key值查找对应的多个value值
        nextNodeList = (List<String>) Edges.get(present_location);

        return nextNodeList;
    }

    /**
     * 获取某个地点的道具列表
     *
     * @param present_location
     * @return
     */
    public static List<String> GetArtefactsByLocation(String present_location) {
        //中间值
        Map<String, Object> position_value = null;
        Map<String, Object> entity_value = null;
        Map<String, String> artefact_value = null;
        //返回 描述文字+物品名称的组合
        List<String> resultList = new ArrayList<String>();

        position_value = (Map<String, Object>) GameMap.get(present_location);

        //如果要找到的地点不在地图 返回空
        if (position_value == null) {
            return resultList;
        }

        entity_value = (Map<String, Object>) position_value.get("entity");

        artefact_value = (Map<String, String>) entity_value.get("artefacts");

        //如果该节点存在artefacts节点
        if (artefact_value != null) {
            for (Map.Entry<String, String> entry : artefact_value.entrySet()) {
                resultList.add(entry.getKey() + " (" + entry.getValue() + ")");
            }
        }

        return resultList;
    }

    /**
     * 获取某个地点的家具列表
     *
     * @param present_location
     * @return
     */
    public static List<String> GetFurnitureByLocation(String present_location) {
        //中间值
        Map<String, Object> position_value = null;
        Map<String, Object> entity_value = null;
        Map<String, String> artefact_value = null;
        //返回 描述文字+物品名称的组合
        List<String> resultList = new ArrayList<String>();

        position_value = (Map<String, Object>) GameMap.get(present_location);

        //如果要找到的地点不在地图 返回空
        if (position_value == null) {
            return resultList;
        }

        entity_value = (Map<String, Object>) position_value.get("entity");

        artefact_value = (Map<String, String>) entity_value.get("furniture");

        //如果该节点存在artefacts节点
        if (artefact_value != null) {
            for (Map.Entry<String, String> entry : artefact_value.entrySet()) {
                resultList.add(entry.getKey() + " (" + entry.getValue() + ")");
            }
        }

        return resultList;
    }

    /**
     * 获取某个地点的人物列表
     *
     * @param present_location
     * @return
     */
    public static List<String> GetCharactersByLocation(String present_location) {
        //中间值
        Map<String, Object> position_value = null;
        Map<String, Object> entity_value = null;
        Map<String, String> artefact_value = null;
        //返回 描述文字+物品名称的组合
        List<String> resultList = new ArrayList<String>();

        position_value = (Map<String, Object>) GameMap.get(present_location);

        //如果要找到的地点不在地图 返回空
        if (position_value == null) {
            return resultList;
        }

        entity_value = (Map<String, Object>) position_value.get("entity");

        artefact_value = (Map<String, String>) entity_value.get("characters");

        //如果该节点存在artefacts节点
        if (artefact_value != null) {
            for (Map.Entry<String, String> entry : artefact_value.entrySet()) {
                resultList.add(entry.getKey() + " (" + entry.getValue() + ")");
            }
        }

        return resultList;
    }

    /**
     * 获取某个地点的描述信息
     *
     * @param present_location
     * @return
     */
    public static String GetDescriptionByLocation(String present_location) {

        Map<String, Object> position_value = (Map<String, Object>) GameMap.get(present_location);

        //地点不存在的情况
        if (position_value == null) {
            return "";
        }

        return position_value.get("description").toString();
    }

    /**
     * 根据地点和道具名称获取道具的描述 此时道具没有被拾取
     *
     * @param present_location
     * @param artefact
     * @return
     */
    public static String GetDescriptionByLocationAndArtefact(String present_location, String artefact) {
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

    /**
     * 根据玩家和道具名称获取道具描述 此时道具已经被拾取 在玩家的背包
     *
     * @param playerName
     * @param artefact
     * @return
     */
    public static String GetDescriptionByPlayerAndArtefact(String playerName, String artefact) {
        Map<String, Object> player_value = (Map<String, Object>) UserMap.get(playerName);
        Map<String, String> artefacts_value;

        if (player_value != null) {
            artefacts_value = (Map<String, String>) player_value.get("artefacts");
            return artefacts_value.get(artefact);
        } else {
            return "";
        }
    }

    /**
     * 将地图内容内容加载到Map数据结构
     *
     * @param subGraphs
     * @return
     */
    public static Map<String, Object> GetMapFromGraph(ArrayList<Graph> subGraphs) {
        //地点
        Map<String, Object> position = new HashMap<String, Object>();
        //每个地点的描述和实体
        Map<String, Object> position_value = new HashMap<String, Object>();
        //实体
        Map<String, Object> entity = new HashMap<String, Object>();
        //实体内容
        Map<String, Object> entity_value = new HashMap<String, Object>();

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

                    entity.put(new String(g2.getId().getId()), new HashMap<String, Object>(entity_value));
                    entity_value.clear();
                }
                position_value.put("entity", new HashMap<String, Object>(entity));
                entity.clear();

                position.put(new String(nLoc.getId().getId()), new HashMap<String, Object>(position_value));
            }
        }

        return position;
    }

    /**
     * 获取有向图的所有有方向的边
     *
     * @param subGraphs
     * @return
     */
    public static Map<String, List<String>> GetEdges(ArrayList<Graph> subGraphs) {

        //返回的有向边集合
        Map<String, List<String>> edgeList = new HashMap<String,List<String>>();

        List<String> temp;

        //一条边的起始和目的节点
        String temp_source_location = "";
        String temp_target_location = "";

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
                if(edgeList.containsKey(temp_source_location)){
                    temp = new ArrayList<String>();
                    temp = edgeList.get(temp_source_location);
                    if(!temp.contains(temp_target_location)){
                     temp.add(new String(temp_target_location));
                     edgeList.put(new String(temp_source_location),new ArrayList<String>(temp));
                    }
                }else {
                    temp = new ArrayList<String>();
                    temp.add(new String(temp_target_location));
                    edgeList.put(new String(temp_source_location),new ArrayList<String>(temp));
                }
            }
        }

        return edgeList;
    }

    /**
     * 解析地图 增强理解 程序中没有调用
     *
     * @param subGraphs
     * @return
     */
    public static Map<String, Map<String, String>> GetMapFromGraph_(ArrayList<Graph> subGraphs) {
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

    /**
     * 获取地图的开始地点
     *
     * @param subGraphs
     * @return
     */
    public static String GetStartLocation(ArrayList<Graph> subGraphs) {
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

    /**
     * 通过用户名初始化用户
     *
     * @param playerName
     * @return
     */
    public static void InitPlayer(String playerName) {
        /**
         * 初始化用户之前要判断该用户是否已经存在
         */

        if (IsPlayerExisted(playerName)) {
            return;
        }

        //玩家的道具
        Map<String, String> artefacts_value = new HashMap<String, String>();
        //玩家
        Map<String, Object> player_value = new HashMap<String, Object>();

        //道具
        player_value.put("artefacts", new HashMap<String, Object>(artefacts_value));
        //健康值
        player_value.put("health", 6);
        //将开始位置设置为当前位置
        player_value.put("location", new String(StartLocation));

        //将初始化后的玩家加入到全局玩家列表
        UserMap.put(new String(playerName), new HashMap<String, Object>(player_value));
    }

    /**
     * 判断玩家名称是否已经存在
     *
     * @param playerName
     * @return
     */
    public static boolean IsPlayerExisted(String playerName) {
        if (UserMap.containsKey(playerName)) {
            return true;
        }
        return false;
    }
}
