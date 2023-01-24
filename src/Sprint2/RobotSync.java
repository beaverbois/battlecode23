package Sprint2;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;

//import static Sprint2.Launcher.target;


public class RobotSync {

    static int minReportDist = 3, maxSwarmDist = 10;
    static int enemyLocMin = 32, enemyLocMax = 44;
    private static final int ISLAND_MIN_INDEX = 23;
    private static final int ISLAND_MAX_INDEX = 47;

//    public static void writeIslands(RobotController rc, boolean haveAnchor) throws GameActionException {
//        if (!rc.canWriteSharedArray(0, 1)) {
//            System.out.println("Could not write to shared array!");
//            return;
//        }
//
//        String data = String.valueOf(rc.readSharedArray(ISLAND_MIN_INDEX));
//        int val = Integer.parseInt(String.valueOf(data.charAt(hqNum)));
//    }
//
//    public static int readIsland(RobotController rc) throws GameActionException {
//
//    }
//
//    public static boolean isIslandDiscovered(RobotController rc, MapLocation islandLoc, int islandIndex) throws GameActionException {
//
//    }
//
//    public static int getNumIslandsFound(RobotController rc) throws GameActionException {
//
//    }

    public static void writeIsland(RobotController rc, int val) throws GameActionException {
        if (!rc.canWriteSharedArray(0, 1)) {
            System.out.println("Could not write to shared array!");
            return;
        }

        rc.writeSharedArray(ISLAND_MIN_INDEX, val);
    }

    public static int readIsland(RobotController rc) throws GameActionException {
        return rc.readSharedArray(ISLAND_MIN_INDEX);
    }
}
