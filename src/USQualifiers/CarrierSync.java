package USQualifiers;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static USQualifiers.Carrier.islands;
import static USQualifiers.Launcher.*;
import static USQualifiers.RobotPlayer.rng;
import static USQualifiers.Util.*;

public class CarrierSync {
    private static final int WELL_MIN_INDEX = 56;
    private static final int SPAWN_ID_MIN_INDEX = 48;
    private static final int CARRIER_ASSIGNMENT_INDEX = 55;
    private static final int MIN_ISLANDS_INDEX = 23;
    private static final int MAX_ISLANDS_INDEX = 47;


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

        //Also throwing errors
        //String data = String.valueOf(rc.readSharedArray(CARRIER_ASSIGNMENT_INDEX));
        //int val = Integer.parseInt(String.valueOf(data.charAt(hqNum)));
        int read = rc.readSharedArray(CARRIER_ASSIGNMENT_INDEX);
        int val = read / (int) Math.pow(10, hqNum) % 10;
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

        //This was throwing errors, so I'm just converting it to an integer. No actual change.
        //StringBuilder data = (read == 0) ? new StringBuilder("00000") : new StringBuilder(String.valueOf(read));
        //data.setCharAt(hqNum, String.valueOf(type.resourceID).charAt(0));
        //System.out.println("HQ: " + data);

        int mod = (int) Math.pow(10, hqNum);
        int dat = read / (10 * mod) * (10 * mod) + type.resourceID * mod + read % mod;

        //rc.writeSharedArray(CARRIER_ASSIGNMENT_INDEX, Integer.parseInt(String.valueOf(data)));
        rc.writeSharedArray(CARRIER_ASSIGNMENT_INDEX, dat);
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

    public static void writeIslands(RobotController rc) throws GameActionException {
        if(islands == null) {
            System.out.println("No islands");
            return;
        }
        if(!rc.canWriteSharedArray(0, 0)) {
            System.out.println("Can't write");
            return;
        }

        for (Map.Entry<Integer, Integer> entry : islands.entrySet()) {
            Integer id = entry.getKey();
            Integer loc = entry.getValue();
            for (int j = MIN_ISLANDS_INDEX; j < MAX_ISLANDS_INDEX; j += 3) {
                int read = rc.readSharedArray(j);
                int id1 = read / 100, id2 = read % 100;
                if (id == id1 || id == id2) break;
                if (id1 == 0) {
                    //Write in digits 1 and 2. Writes in id1 before id2.
                    rc.writeSharedArray(j, id * 100);
                    rc.writeSharedArray(j+1, loc);
                    break;
                }
                if (id2 == 0) {
                    //Write in digits 3 and 4.
                    rc.writeSharedArray(j, id + id1 * 100);
                    rc.writeSharedArray(j+2, loc);
                    break;
                }
            }
        }
    }

    //May use too much bytecode, will need to check.
    public static void readIslands(RobotController rc) throws GameActionException {
        for (int i = MIN_ISLANDS_INDEX; i < MAX_ISLANDS_INDEX; i += 3) {
            int read = rc.readSharedArray(i);
            if(read == 0) continue;
            int id1 = read / 100, id2 = read % 100;
            int island1 = rc.readSharedArray(i+1), island2 = rc.readSharedArray(i+2);
            if(id1 != 0 && !islands.containsKey(id1) && island1 / 10000 != 1) islands.put(id1, island1);
            else if(id1 != 0 && islands.containsKey(id1) && island1 / 10000 == 1) islands.replace(id1, island1);
            if(id2 != 0 && !islands.containsKey(id2) && island2 / 10000 != 1) islands.put(id2, island2);
            else if(id2 != 0 && islands.containsKey(id2) && island2 / 10000 == 1) islands.replace(id2, island2);
        }
    }
}
