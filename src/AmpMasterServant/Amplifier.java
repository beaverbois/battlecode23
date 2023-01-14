package AmpMasterServant;

import battlecode.common.*;

import java.util.HashMap;

import static RushWithAnchors.RobotPlayer.*;
import static Utilities.Util.*;

public class Amplifier {

    static enum AmpState {
        ALPHA,
        RADIO
    }
    static AmpState astate = AmpState.ALPHA;

    static HashMap<Integer, MapLocation> alphas = new HashMap<>();
    static int[] alphaIDs = new int[4];

    static int alphaIndex;

    static MapLocation pos;

    static MapLocation target;
    static boolean targetHQ = false;

    static int numFriends = 0; // :(

    static int lastMoveTurn = -20;

    public static void run(RobotController rc) throws GameActionException {
        switch(astate) {
            case ALPHA: alpha(rc); break;
            case RADIO: radio(rc); break;
        }
    }

    private static void turnStart(RobotController rc) throws GameActionException {
        rc.setIndicatorString(target + ", " + numFriends);

        pos = rc.getLocation();

        for(int i = 0; i < allHQ.length; i++) {
            allOpposingHQ[i] = intToLoc(rc.readSharedArray(i + 4));
        }

        //Will need to adjust to only include launchers.
        RobotInfo[] friends = rc.senseNearbyRobots(20, rc.getTeam());
        numFriends = 0;
        for(RobotInfo friend : friends) {
            if(friend.getType() == RobotType.LAUNCHER) numFriends++;
        }
        if(rc.senseMapInfo(pos).hasCloud()) numFriends = 3;
        rc.writeSharedArray(alphaIndex + 1, locToInt(pos) + (numFriends > 9 ? 10000 : 0));

        for(int i = 24; i < 31; i += 2) {
            int id = rc.readSharedArray(i);
            alphaIDs[i/2 - 12] = id;
            MapLocation loc = intToLoc(rc.readSharedArray(i+1));
            if(!alphas.containsKey(id)) alphas.put(id, loc);
            else alphas.replace(id, loc);
        }
    }

    private static void alpha(RobotController rc) throws GameActionException {
        turnStart(rc);

        int[] unassignedAlphas = alphaIDs.clone();

        if(target == null) {
            for(int i = 32; i < 43; i += 2) {
                int id = rc.readSharedArray(i);
                if(id != 0) {
                    for(int j = 0; j < unassignedAlphas.length; j++)
                        if(unassignedAlphas[j] == id) unassignedAlphas[j] = 0;
                }
            }
            for(int i = 33; i < 44; i+= 2) {
                int read = rc.readSharedArray(i);
                if(read != 0 && rc.readSharedArray(i-1) == 0) {
                    //Need to assign an alpha, choose closest unassigned.
                    MapLocation loc = intToLoc(read);
                    int minDist = distance(pos, loc);
                    int assignedAlpha = rc.getID();
                    int in = 0;
                    for(int j = 0; j < unassignedAlphas.length; j++) {
                        if(unassignedAlphas[j] != 0) {
                            int dist = distance(alphas.get(unassignedAlphas[j]), loc);
                            //May cause problems with same distance alphas.
                            if(dist < minDist) {
                                minDist = dist;
                                assignedAlpha = unassignedAlphas[j];
                                in = j;
                            }
                        }
                    }
                    if(assignedAlpha == rc.getID()) {
                        target = loc;
                        rc.writeSharedArray(i-1, rc.getID());
                        for(int j = 0; j < allHQ.length; j++) {
                            if(allOpposingHQ[j] == target) {
                                targetHQ = true;
                                break;
                            }
                        }
                        break;
                    }
                    unassignedAlphas[in] = 0;
                }
            }
            if(target == null && numFriends > 2) moveAway(rc, headquarters);
        } else if(distance(pos, target) > 2) {
            if(numFriends > 2) moveTowards(rc, target);
        } else if(!targetHQ){
            int radius = rc.getType().actionRadiusSquared;
            Team opponent = rc.getTeam().opponent();
            RobotInfo[] enemies = rc.senseNearbyRobots(radius, opponent);
            if(enemies.length == 0) {
                target = null;
                return;
            } else {
                MapLocation[] enemyLoc = new MapLocation[enemies.length];
                for (int i = 0; i < enemies.length; i++) enemyLoc[i] = enemies[i].getLocation();
                for (int i = 32; i < 43; i += 2)
                    if(rc.readSharedArray(i) == rc.getID()) {
                        rc.writeSharedArray(i + 1, locToInt(target = closest(pos, enemyLoc)));
                        break;
                    }
                if(numFriends > 2) moveTowards(rc, target);
            }
        }

        if(target != null) {
            for (MapLocation mapLocation : allHQ) {
                if (target == mapLocation) {
                    targetHQ = true;
                    break;
                }
            }

            //Inform if no need for extra bots
            for (int i = 32; i < 43; i += 2) {
                if (rc.readSharedArray(i) == rc.getID()) {
                    rc.writeSharedArray(i + 1, locToInt(target) + numFriends > 10 ? 10000 : 0);
                    break;
                }
            }
        }

        if(rc.getHealth() < 13) {
            //You boutta die, better report that.
            astate = AmpState.RADIO;
            rc.writeSharedArray(alphaIndex, 0);
            rc.writeSharedArray(alphaIndex + 1, 0);
            radio(rc);
        }
    }

    private static void radio(RobotController rc) throws GameActionException {
        //You're literally a radio, just vibe.
        pos = rc.getLocation();
        headquarters = closest(pos, allHQ);
        if(distance(pos, headquarters) > 6) moveTowards(rc, headquarters);
    }


    private static void moveAway(RobotController rc, MapLocation from) throws GameActionException {
        //Match pace to launchers.
        if(astate == AmpState.ALPHA && turnCount - lastMoveTurn < 3) return;
        int dirIn = away(pos, from);
        if(dirIn == -1) {
            moveRandom(rc);
            return;
        }
        int randInt = rng.nextInt(3);
        Direction dir = directions[(dirIn + (randInt - 1) + directions.length) % directions.length];
        if (rc.canMove(dir)) rc.move(dir);
        else if (rc.canMove(directions[(dirIn + (randInt % 2) + directions.length - 1) % directions.length]))
            rc.move(directions[(dirIn + (randInt % 2) + directions.length - 1) % directions.length]);
        else if(rc.canMove(directions[(dirIn + (randInt + 1 % 2) + directions.length - 1) % directions.length]))
            rc.move(directions[(dirIn + (randInt + 1 % 2) + directions.length - 1) % directions.length]);
        else {
            corner = pos;
            moveRandom(rc);
        }
        lastMoveTurn = turnCount;
    }

    private static void moveTowards(RobotController rc, MapLocation target) throws GameActionException {
        //Match pace to launchers.
        if(astate == AmpState.ALPHA && turnCount - lastMoveTurn < 3) return;
        int dirIn = towards(pos, target);
        if(dirIn == -1) {
            moveRandom(rc);
            return;
        }
        Direction dir = directions[dirIn];
        if (rc.canMove(dir)) rc.move(dir);
        else if (rc.canMove(directions[(dirIn + 1) % directions.length]))
            rc.move(directions[(dirIn + 1) % directions.length]);
        else if(rc.canMove(directions[((dirIn - 1) + directions.length - 1) % directions.length]))
            rc.move(directions[((dirIn - 1) + directions.length - 1) % directions.length]);
        else {
            moveRandom(rc);
        }
        lastMoveTurn = turnCount;
    }

    private static void moveRandom(RobotController rc) throws GameActionException {
        int randDir = rng.nextInt(directions.length);
        Direction dir = directions[randDir++ % directions.length];
        for (int i = 0; i < directions.length && !rc.canMove(dir); i++) {
            dir = directions[randDir++ % directions.length];
        }
        if (rc.canMove(dir)) rc.move(dir);
    }
}
