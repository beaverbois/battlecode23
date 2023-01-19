package Sprint2;

import battlecode.common.*;

import javax.annotation.Resource;

import static Sprint2.Util.intToLoc;
import static Sprint2.Util.locToInt;

public class CarrierSync {
    private static final int WELL_MIN_INDEX = 56;
    private static final int SPAWN_ID_MIN_INDEX = 48;
    private static final int CARRIER_ASSIGNMENT_INDEX = 55;

    public static void writeWell(RobotController rc, ResourceType type, MapLocation loc, int hqNum) throws IndexOutOfBoundsException, GameActionException {
        if (hqNum < 0 || hqNum > GameConstants.MAX_STARTING_HEADQUARTERS - 1) {
            throw new IndexOutOfBoundsException("Hq num out of bounds");
        }

        // If we are too far to write
        if (!rc.canWriteSharedArray(0, 1)) {
            System.out.println("Could not write to shared array!");
            return;
        }

        int index = WELL_MIN_INDEX + (2 * hqNum);
        if (type == ResourceType.MANA) {
            index++;
        }

        rc.writeSharedArray(index, type.resourceID * 10000 + locToInt(loc));
    }

    public static boolean isWellDiscovered(RobotController rc, ResourceType type, int hqNum) throws GameActionException {
        if (hqNum < 0 || hqNum > GameConstants.MAX_STARTING_HEADQUARTERS - 1) {
            throw new IndexOutOfBoundsException("Hq num out of bounds");
        }

        int index = WELL_MIN_INDEX + (2 * hqNum);
        if (type == ResourceType.MANA) {
            index++;
        }

        return rc.readSharedArray(index) != 0;
    }

    public static MapLocation readWellLocation(RobotController rc, ResourceType type, int hqNum) throws IndexOutOfBoundsException, GameActionException {
        if (hqNum < 0 || hqNum > GameConstants.MAX_STARTING_HEADQUARTERS - 1) {
            throw new IndexOutOfBoundsException("Hq num out of bounds");
        }

        int index = WELL_MIN_INDEX + (2 * hqNum);
        if (type == ResourceType.MANA) {
            index++;
        }

        if (rc.readSharedArray(index) == 0) {
            System.out.println("Shared array is empty at index " + index + " for HQ " + hqNum);
        }

        return intToLoc(rc.readSharedArray(index) % 10000);
    }

    public static int readNumWellsFound(RobotController rc, int hqNum) throws GameActionException {
        if (hqNum < 0 || hqNum > GameConstants.MAX_STARTING_HEADQUARTERS - 1) {
            throw new IndexOutOfBoundsException("Hq num out of bounds");
        }

        int total = 0;
        int index = WELL_MIN_INDEX + (2 * hqNum);
        if (rc.readSharedArray(index) != 0) total++;
        if (rc.readSharedArray(++index) != 0) total++;

        return total;
    }

    public static ResourceType readCarrierAssignment(RobotController rc, int hqNum) throws GameActionException {
        if (hqNum < 0 || hqNum > GameConstants.MAX_STARTING_HEADQUARTERS - 1) {
            throw new IndexOutOfBoundsException("Hq num out of bounds");
        }

        String data = String.valueOf(rc.readSharedArray(CARRIER_ASSIGNMENT_INDEX));
        int val = Integer.parseInt(String.valueOf(data.charAt(hqNum)));
        return ResourceType.values()[val];
    }

    public static void writeCarrierAssignment(RobotController rc, ResourceType type, int hqNum) throws GameActionException {
        if (hqNum < 0 || hqNum > GameConstants.MAX_STARTING_HEADQUARTERS - 1) {
            throw new IndexOutOfBoundsException("Hq num out of bounds");
        }

        if (!rc.canWriteSharedArray(0, 1)) {
            System.out.println("Could not write to shared array!");
            return;
        }

        // modify the digit located at index hqNum of carrier assignment index in the shared array using strings
        int read = rc.readSharedArray(CARRIER_ASSIGNMENT_INDEX);
        StringBuilder data = (read == 0) ? new StringBuilder("00000") : new StringBuilder(String.valueOf(read));
        data.setCharAt(hqNum, String.valueOf(type.resourceID).charAt(0));

        rc.writeSharedArray(CARRIER_ASSIGNMENT_INDEX, Integer.parseInt(String.valueOf(data)));
    }

    public static int[] readCarrierSpawnIDs(RobotController rc) throws GameActionException {
        int[] IDs = new int[GameConstants.MAX_STARTING_HEADQUARTERS];
        for (int i = 0; i < IDs.length; i++) {
            IDs[i] = rc.readSharedArray(SPAWN_ID_MIN_INDEX + i);
        }

        return IDs;
    }

    public static void writeCarrierSpawnID(RobotController rc, int ID, int hqNum) throws GameActionException {
        if (hqNum < 0 || hqNum > GameConstants.MAX_STARTING_HEADQUARTERS - 1) {
            throw new IndexOutOfBoundsException("Hq num out of bounds");
        }

        if (!rc.canWriteSharedArray(0, 1)) {
            System.out.println("Could not write to shared array!");
            return;
        }

        int index = SPAWN_ID_MIN_INDEX + hqNum;
        rc.writeSharedArray(index, ID);
    }
}
