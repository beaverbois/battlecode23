package PostS1;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;

import static BeaverBois_S1.CarrierSync.*;
import static BeaverBois_S1.RobotPlayer.*;
import static BeaverBois_S1.Util.*;

public class Launcher {

    static enum LauncherState {
        RUSHING,
        REPORTING,
        SWARMING,
        SUPPRESSING
    }
    static LauncherState lstate = LauncherState.RUSHING;

    static final ArrayList<RobotType> launcherPriority = new ArrayList<RobotType>(Arrays.asList(
            RobotType.DESTABILIZER,
            RobotType.LAUNCHER,
            RobotType.BOOSTER,
            RobotType.CARRIER,
            RobotType.AMPLIFIER,
            RobotType.HEADQUARTERS
    ));

    static RobotInfo[] enemies;

    static MapLocation pos;

    static MapLocation targetOppHQ;
    static int oppHQStatus = 0;

    static int targetEnemy = 0;

    //Wells
    static boolean reportingWell = false;
    static MapLocation targetWellLocation = null;
    static ResourceType targetWellType = null;

    static void run(RobotController rc) throws GameActionException {
        rc.setIndicatorString(lstate.toString());

        switch(lstate) {
            case RUSHING: rushing(rc); break;
            case REPORTING: reporting(rc); break;
            case SWARMING: swarming(rc); break;
            case SUPPRESSING: suppressing(rc); break;
        }
    }

    private static void turnStart(RobotController rc) throws GameActionException {
        pos = rc.getLocation();

        for (int i = 0; i < allHQ.length; i++) {
            int read = rc.readSharedArray(i + 4) % 10000;
            if (read != 0 && read != locToInt(allOpposingHQ[i])) {
                if (locToInt(allOpposingHQ[i]) == 0) {
                    allOpposingHQ[i] = intToLoc(read);
                }
                else if (i < allHQ.length - 1) {
                    allOpposingHQ[i + 1] = allOpposingHQ[i];
                    allOpposingHQ[i] = intToLoc(read);
                }
            }
        }

        int radius = rc.getType().visionRadiusSquared;
        Team opponent = rc.getTeam().opponent();
        enemies = rc.senseNearbyRobots(radius, opponent);
    }

    private static void rushing(RobotController rc) throws GameActionException {
        turnStart(rc);

        attack(rc);

        scout(rc);
        if(lstate == LauncherState.REPORTING) return;

        int count = 0;
        int maxDist = 10; //Max distance to swarm towards.
        int tooClose = 4; //Min distance before a target is considered new.
        MapLocation[] untakenHQ = new MapLocation[4];
        for (int i = 0; i < allOpposingHQ.length; i++) {
            if (locToInt(allOpposingHQ[i]) == 0) break;
            if (rc.readSharedArray(i + 4) / 10000 == 0) untakenHQ[count++] = allOpposingHQ[i];
        }
        if(count != 0) {
            MapLocation[] knownOppHQ = new MapLocation[count];
            System.arraycopy(untakenHQ, 0, knownOppHQ, 0, count);
            MapLocation close = closest(pos, knownOppHQ);
            if(distance(pos, close) < maxDist) {
                targetOppHQ = close;
                lstate = LauncherState.SWARMING;
                Direction to = directions[towards(pos, targetOppHQ)];
                if(rc.isMovementReady() && rc.canMove(to)) rc.move(to);
            }
        }

        if (rc.isMovementReady()) {
            //If an enemy launcher is seen, move some units towards it.
            MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            MapLocation target = new MapLocation(120, 120);
            int minDist = distance(pos, target);
            for(int i = 32; i < 44; i++) {
                int read = rc.readSharedArray(i);
                MapLocation loc = intToLoc(read);
                int dist = distance(pos, loc);
                if(read != 0 && dist < tooClose && enemies.length < 2) {
                    lstate = LauncherState.REPORTING;
                    targetEnemy = read;
                    moveTowards(rc, headquarters);
                    return;
                }
                else if(read != 0 && dist < minDist) {
                    //If it's by an oppHQ, don't bother.
                    boolean close = false;
                    for(int j = 0; j < allHQ.length; j++) {
                        if(distance(loc, allOpposingHQ[j]) < tooClose) {
                            close = true;
                            break;
                        }
                    }

                    if(!close) {
                        //Close-ish to the target, go for it.
                        target = loc;
                        minDist = dist;
                    }
                }
            }

            //Go towards target
            if(distance(pos, target) < 40) {
                rc.setIndicatorString("Rushing " + target);
                moveTowards(rc, target);
                return;
            }

            if(reportingWell && enemies.length == 0) {
                lstate = LauncherState.REPORTING;
                return;
            }

            int radius = rc.getType().visionRadiusSquared;
            Team ally = rc.getTeam();
            RobotInfo[] allies = rc.senseNearbyRobots(radius, ally);

            //Trying running to the center
            if(distance(pos, center) < 3 && allies.length > 3) moveTowards(rc, new MapLocation(rc.getMapWidth() - headquarters.x, rc.getMapHeight() - headquarters.y));
            else moveTowards(rc, center);
        }
    }

    private static void reporting(RobotController rc) throws GameActionException {
        turnStart(rc);

        attack(rc);

        if (rc.canWriteSharedArray(0, 0)) {
            //Update shared array with enemy HQ and status updates on suppressed HQ.
            for(int i = 0; i < allHQ.length; i++) {
                int read = rc.readSharedArray(4+i);
                int oppHQ = locToInt(allOpposingHQ[i]);
                if (oppHQ != 0 && (read == 0 || (targetOppHQ != null && targetOppHQ.equals(allOpposingHQ[i])))) {
                    if(targetOppHQ != null && targetOppHQ.equals(allOpposingHQ[i])) rc.writeSharedArray(4 + i, 10000 * oppHQStatus + oppHQ);
                    else rc.writeSharedArray(4 + i, oppHQ);
                }
            }
            if(targetEnemy != 0) {
                for (int i = 32; i < 44; i++) {
                    int read = rc.readSharedArray(i);
                    int dist = distance(intToLoc(targetEnemy), intToLoc(read));
                    if (dist < 3) {
                        rc.writeSharedArray(i, 0);
                    }
                }
                targetEnemy = 0;
            }

            //Wells
            if(reportingWell) {
                ArrayList<MapLocation> targetWellLocations = new ArrayList<>();
                for (int i = wellIndexMin; i <= wellIndexMax; i++) {
                    if (getWellType(rc, i) == targetWellType) targetWellLocations.add(getWellLocation(rc, i));
                }

                if (targetWellLocations.contains(targetWellLocation)) {
                    reportingWell = false;
                } else if (rc.canWriteSharedArray(0, 1)) {
                    writeWell(rc, targetWellType, targetWellLocation);

                    System.out.println(targetWellType + " at " + targetWellLocation);
                    System.out.println("Wells Discovered: " + getNumWellsFound(rc));
                    reportingWell = false;
                }
            }
            lstate = LauncherState.RUSHING;
        } else if (rc.isMovementReady()) {
            //Move towards closest headquarters.
            headquarters = closest(pos, allHQ);
            moveTowards(rc, headquarters);
        }
    }

    private static void swarming(RobotController rc) throws GameActionException {
        turnStart(rc);

        attack(rc);

        Team ally = rc.getTeam();
        RobotInfo[] allies = rc.senseNearbyRobots(targetOppHQ, 4, ally);

        int numAttackers = allies.length;
        int numDefenders = enemies.length;

        for(RobotInfo bot : allies) if(bot.getType() != RobotType.LAUNCHER) numAttackers--;

        for(RobotInfo bot : enemies) if(bot.getType() != RobotType.LAUNCHER && bot.getType() != RobotType.DESTABILIZER) numDefenders--;

        int suppressiveForce = 2;

        int difference = numAttackers - numDefenders;

        if (rc.isMovementReady()) {
            moveTowards(rc, targetOppHQ);
        }
        if(distance(pos, targetOppHQ) < 2) lstate = LauncherState.SUPPRESSING;
        if(distance(pos, targetOppHQ) < 4 && difference > suppressiveForce) {
            oppHQStatus = 1;
            lstate = LauncherState.REPORTING;
        }
    }

    private static void suppressing(RobotController rc) throws GameActionException {
        //Tentatively intentionally allowing HQ to spawn bots to waste resources.
        turnStart(rc);

        attack(rc);

        if(rc.isMovementReady() && distance(pos, targetOppHQ) > 1) moveTowards(rc, targetOppHQ);

        Team ally = rc.getTeam();
        RobotInfo[] allies = rc.senseNearbyRobots(targetOppHQ, 4, ally);

        int numAttackers = allies.length;

        for(RobotInfo bot : allies) if(bot.getType() != RobotType.LAUNCHER) numAttackers--;

        if(distance(pos, targetOppHQ) > 1 && numAttackers > 3) lstate = LauncherState.RUSHING;
    }

    private static void scout(RobotController rc) throws GameActionException {
        //Well scouting
        // when we discover a nearby well, make sure it is the right type and not already stored before we write it
        WellInfo[] wells = rc.senseNearbyWells();
        if (wells.length > 0) {
            // make a location list of all stored wells of each type
            ArrayList<MapLocation> adWellLocations = new ArrayList<>();
            ArrayList<MapLocation> mnWellLocations = new ArrayList<>();
            for (int i = wellIndexMin; i <= wellIndexMax; i++) {
                if (getWellType(rc, i) == ResourceType.ADAMANTIUM) adWellLocations.add(getWellLocation(rc, i));
                else if (getWellType(rc, i) == ResourceType.MANA) mnWellLocations.add(getWellLocation(rc, i));
            }

            // we only want to store numWellsStored/2 wells per type, not elixir yet
            if (adWellLocations.size() < numWellsStored / 2 || mnWellLocations.size() < numWellsStored / 2) {
                // check if any wells we found are new and not stored
                for (WellInfo well : wells) {
                    MapLocation loc = well.getMapLocation();
                    ResourceType type = well.getResourceType();
                    if ((mnWellLocations.size() < numWellsStored / 2 && mnWellLocations.contains(loc)) || (adWellLocations.size() < numWellsStored / 2 && adWellLocations.contains(loc))) {
                        System.out.println("writing well");
                        targetWellLocation = loc;
                        targetWellType = type;
                        // if we can write new well, do so
                        if (rc.canWriteSharedArray(0, 1)) {
                            writeWell(rc, type, loc);
                        } else {
                            // otherwise, return to hq to report
                            reportingWell = true;
                            break;
                        }
                    }
                }
            }

        }

        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == RobotType.HEADQUARTERS) {
                int loc = locToInt(enemy.getLocation());
                int numHQ = allHQ.length;
                for (int i = 0; i < numHQ; i++) {
                    int val = locToInt(allOpposingHQ[i]);
                    if (val == loc) {
                        break;
                    }
                    if (val == 0) {
                        allOpposingHQ[i] = enemy.getLocation();
                        lstate = LauncherState.REPORTING;
                        System.out.println("Spotted HQ " + enemy.getLocation());
                        break;
                    }
                }
                return;
            }
        }
    }

    private static void attack(RobotController rc) throws GameActionException {
        int targetPrio;
        if (rc.isActionReady() && enemies.length > 0) {
            // MapLocation toAttack = enemies[0].location;
            targetPrio = launcherPriority.indexOf(enemies[0].getType());
            int targetHealth = enemies[0].getHealth();
            MapLocation target = enemies[0].location;
            for (RobotInfo enemy : enemies) {
                MapLocation loc = enemy.location;
                if(rc.canActLocation(loc)) {
                    RobotType etype = enemy.getType();
                    int eprio = launcherPriority.indexOf(etype);
                    if (eprio < targetPrio || (eprio <= targetPrio && enemy.getHealth() < targetHealth)) {
                        targetHealth = enemy.getHealth();
                        targetPrio = launcherPriority.indexOf(etype);
                        target = loc;
                    }
                }
            }
            if (rc.canAttack(target)) {
                rc.attack(target);
            }            if (rc.isMovementReady() && lstate == LauncherState.RUSHING) {
                moveTowards(rc, target);
            }
        }
    }


    private static void moveRandom(RobotController rc) throws GameActionException {
        int randDir = rng.nextInt(directions.length);
        Direction dir = directions[randDir++ % directions.length];
        for (int i = 0; i < directions.length && !rc.canMove(dir); i++) {
            dir = directions[randDir++ % directions.length];
        }
        if (rc.canMove(dir)) rc.move(dir);
    }


    private static void moveAway(RobotController rc, MapLocation from) throws GameActionException {
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
    }

    private static void moveTowards(RobotController rc, MapLocation target) throws GameActionException {
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
    }

}
