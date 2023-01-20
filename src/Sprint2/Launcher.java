package Sprint2;

import battlecode.common.*;

import java.awt.*;
import java.util.*;
import java.util.List;

import static Sprint2.CarrierSync.*;
import static Sprint2.HQSync.readHQLocation;
import static Sprint2.HQSync.readNumHQs;
import static Sprint2.LauncherSync.*;
import static Sprint2.RobotPlayer.*;
import static Sprint2.Util.*;

public class Launcher {

    static enum LauncherState {
        GATHERING,
        REPORTING,
        SWARMING,
        SUPPRESSING,
        PACK
    }
    static LauncherState lstate = LauncherState.GATHERING;

    static final ArrayList<RobotType> launcherPriority = new ArrayList<RobotType>(Arrays.asList(
            RobotType.DESTABILIZER,
            RobotType.LAUNCHER,
            RobotType.BOOSTER,
            RobotType.CARRIER,
            RobotType.AMPLIFIER,
            RobotType.HEADQUARTERS
    ));

    static RobotInfo[] nearbyRobots, enemies, allies;

    static RobotInfo[] packStatus;

    static MapLocation pos;

    static MapLocation target;
    static int oppHQStatus = 0;

    static boolean withinOppHQRange = false;
    static boolean targetReported = false;

    static final int MIN_PACK_SIZE = 6, RETREAT = 3;

    //Wells
    static boolean reportingWell = false, reportingEnemy = false, reportingHQ = false, reportingSuspect = false;
    static MapLocation targetWellLocation = null;
    static ResourceType targetWellType = null;

    static final int TOWARDS_CENTER_MOD = 2;

    static MapLocation[] suspectedOppHQ;
    static int suspectCount = 0;
    static boolean stateLock = false;
    static MapLocation[] allHQ;
    static MapLocation[] allOpposingHQ;
    static MapLocation headquarters = new MapLocation(0, 0);
    static MapLocation corner = new MapLocation(-1, -1);
    static MapLocation newKnownHQ;

    static Direction pastWall;
    static boolean stuck = false;

    static ArrayList<MapLocation> lastPos = new ArrayList<>();
    static int lastPosSize = 5;

    static void run(RobotController rc) throws GameActionException {
        if (!stateLock) {
            setup(rc);
            stateLock = true;
        }

        rc.setIndicatorString(lstate.toString());

        switch(lstate) {
            case GATHERING: gathering(rc); break;
            case REPORTING: reporting(rc); break;
            case SWARMING: swarming(rc); break;
            case SUPPRESSING: suppressing(rc); break;
            case PACK: pack(rc); break;
        }

        lastPos.add(pos);
        if(lastPos.size() > lastPosSize) lastPos.remove(0);
    }

    private static void turnStart(RobotController rc) throws GameActionException {
        pos = rc.getLocation();

        readOppHeadquarters(rc);

        updateSuspected(rc);
        int temp = suspectCount;
        while(suspectedOppHQ[suspectCount].equals(new MapLocation(120, 120))) {
            suspectCount = (suspectCount + 1) % suspectedOppHQ.length;
            if(suspectCount == temp) break;
        }

        //If reporting and suspectCount changes, someone else has reported.
        if(reportingSuspect && temp != suspectCount) lstate = LauncherState.GATHERING;

        nearbyRobots = rc.senseNearbyRobots();
        List<RobotInfo> alliesL = new ArrayList<>();
        List<RobotInfo> enemiesL = new ArrayList<>();

        for (RobotInfo bot : nearbyRobots) {
            if (bot.team == rc.getTeam() && bot.type == RobotType.LAUNCHER) alliesL.add(bot);
            else if (bot.team == rc.getTeam().opponent()) enemiesL.add(bot);
        }

        allies = new RobotInfo[alliesL.size()];
        enemies = new RobotInfo[enemiesL.size()];

        allies = alliesL.toArray(allies);
        enemies = enemiesL.toArray(enemies);

        if (packStatus == null) packStatus = allies;

        scout(rc);
    }

    private static void gathering(RobotController rc) throws GameActionException {
        turnStart(rc);

        attack(rc);

        if (lstate == LauncherState.REPORTING) return;

        if (rc.isMovementReady()) {
            //If an enemy launcher is seen, move some units towards it.
            //Trying moving towards the center of all HQ, then moving out as a pack.
            int centerX = 0, centerY = 0;
            for (MapLocation mapLocation : allHQ) {
                centerX += mapLocation.x;
                centerY += mapLocation.y;
            }
            MapLocation hqCenter = new MapLocation(centerX / allHQ.length, centerY / allHQ.length);

            int towardsCenterX, towardsCenterY;

            MapLocation gatherPoint;

            //Gather

            towardsCenterX = (rc.getMapWidth() / 2 - hqCenter.x) / TOWARDS_CENTER_MOD;
            towardsCenterY = (rc.getMapHeight() / 2 - hqCenter.y) / TOWARDS_CENTER_MOD;

            gatherPoint = new MapLocation(hqCenter.x + towardsCenterX, hqCenter.y + towardsCenterY);

            rc.setIndicatorString("GATHERING, " + gatherPoint);

            if(!stuck) {
                boolean testStuck = true;
                if(lastPos.size() >= lastPosSize) {
                    double avrX = 0, avrY = 0;
                    for(MapLocation loc : lastPos) {
                        avrX += loc.x;
                        avrY += loc.y;
                    }
                    avrX /= lastPos.size();
                    avrY /= lastPos.size();

                    if(Math.abs(avrX - pos.x) >= 2 || Math.abs(avrY - pos.y) >= 2) testStuck = false;
                }
                Direction[] close = closeDirections(rc, pos, gatherPoint);
                if(!testStuck) {
                    testStuck = true;
                    for (int i = 0; i < 2; i++) {
                        if (rc.canMove(close[i])) {
                            testStuck = false;
                            break;
                        }
                    }
                }
                stuck = testStuck;
                if(stuck) {
                    for (int i = 3; i < close.length; i++) {
                        if (close[i] != Direction.CENTER && rc.onTheMap(pos.add(close[i])) && rc.sensePassability(pos.add(close[i]))) {
                            pastWall = close[i];
                            break;
                        }
                    }
                    if (pastWall == null) {
                        //Literally no possible moves, just chill.
                        stuck = false;
                        attack(rc);
                        packStatus = allies;
                        return;
                    }
                }
            }

            //Choose a direction and stick to it.
            if(distance(pos, gatherPoint) > 1 && stuck) {
                rc.setIndicatorString("Stuck, " + gatherPoint + ", " + pastWall);
                if(rc.canMove(pos.directionTo(gatherPoint))) {
                    stuck = false;
                    pastWall = null;
                    moveTowards(rc, gatherPoint);
                } else if(pastWall == null);
                else if(rc.onTheMap(pos.add(pastWall)) && !rc.sensePassability(pos.add(pastWall))) {
                    Direction[] close = closeDirections(rc, pos, gatherPoint);
                    for(int i = 0; i < close.length; i++) {
                        if(close[i].opposite() == pastWall) continue;
                        if(rc.canMove(close[i])) {
                            pastWall = close[i];
                            rc.move(pastWall);
                        }
                    }
                    //Shouldn't ever get past the last case.
                } else moveTowards(rc, pos.add(pastWall));
            }

            else if (distance(pos, gatherPoint) > 0) moveTowards(rc, gatherPoint);

            MapLocation[] t = closestTargetHQ(rc);

            if(t != null && (allies.length > MIN_PACK_SIZE || withinOppHQRange)) {
                lstate = LauncherState.SWARMING;
                target = t[0];
            }
            else if(allies.length > MIN_PACK_SIZE) {
                lstate = LauncherState.PACK;
                chooseTarget(rc);
            }

            attack(rc);

            packStatus = allies;
        }
    }

    private static void reporting(RobotController rc) throws GameActionException {
        turnStart(rc);
        if(lstate == LauncherState.GATHERING) return;

        attack(rc);

        if (rc.canWriteSharedArray(0, 0)) {
            if (reportingHQ) {
                reportHQ(rc);
                reportingHQ = false;
            }

            if(reportingSuspect) {
                writeSuspected(rc, false);
                reportingSuspect = false;
            }

            if(reportingEnemy) {
                reportEnemy(rc, target, targetReported);
                reportingEnemy = false;
            }

            if (reportingWell) {
                // TODO: Eventually ugprade to new well system
//                reportWell(rc, targetWellLocation, targetWellType);
                reportingWell = false;
            }

            lstate = LauncherState.GATHERING;
        } else if (rc.isMovementReady()) {
            //Move towards closest headquarters.
            headquarters = closest(pos, allHQ);
            moveTowards(rc, headquarters);
        }

        attack(rc);
    }

    private static void swarming(RobotController rc) throws GameActionException {
        turnStart(rc);

        attack(rc);

        if(allies.length < RETREAT && !withinOppHQRange) {
            lstate = LauncherState.GATHERING;
            return;
        }

        int avrX = pos.x, avrY = pos.y, suppressiveForce = 6;

        //TODO: Implement moving towards injured allies (aka health decreased since last turn).
        for(RobotInfo ally : allies) {
            avrX += ally.getLocation().x;
            avrY += ally.getLocation().y;
        }

        MapLocation avrPos = new MapLocation(avrX / (allies.length + 1), avrY / (allies.length + 1));

        //If too far ahead, don't move.
        if(distance(pos, target) > distance(avrPos, target) + 2);
            //If far from pack, move towards them.
        else if(distance(pos, avrPos) > 3) moveTowards(rc, avrPos);
        else moveTowards(rc, target);

        if(distance(pos, target) < 3) lstate = LauncherState.SUPPRESSING;
        if(distance(pos, target) < 3 && allies.length - enemies.length > suppressiveForce) {
            MapLocation[] close = closestTargetHQ(rc);
            if(close != null && close[0] == target) {
                oppHQStatus = 1;
                lstate = LauncherState.REPORTING;
                reportingHQ = true;
            }
            else if(close != null) target = close[0];
        }

        attack(rc);

        packStatus = allies;
    }

    private static void suppressing(RobotController rc) throws GameActionException {
        turnStart(rc);

        attack(rc);

        if (rc.isMovementReady() && withinSquaredRadius(pos, target, 9)) moveAway(rc, target);
        else if (rc.isMovementReady() && dist(pos, target) > 4) moveTowards(rc, target);
//
//
//        Team ally = rc.getTeam();
//        RobotInfo[] allies = rc.senseNearbyRobots(target, 4, ally);
//
//        int numAttackers = allies.length;
//
//        for (RobotInfo bot : allies) if (bot.getType() != RobotType.LAUNCHER) numAttackers--;
//
//        if(distance(pos, target) > 1 && numAttackers > 3) lstate = LauncherState.GATHERING;

        attack(rc);
    }

    private static void pack(RobotController rc) throws GameActionException {
        turnStart(rc);
        chooseTarget(rc);

        rc.setIndicatorString("Pack " + target);

        attack(rc);

        if(lstate == LauncherState.REPORTING) return;
        if(enemies.length == 0 && distance(pos, target) < 3) {
            System.out.println("Spotted empty, " + target);
            lstate = LauncherState.REPORTING;
            if(targetReported) reportingEnemy = true;
            else reportingSuspect = true;
            return;
        }



        if(allies.length < RETREAT) {
            lstate = LauncherState.GATHERING;
            return;
        }

        int avrX = pos.x, avrY = pos.y;

        //TODO: Implement moving towards injured allies (aka health decreased since last turn).
        for (RobotInfo ally : allies) {
            avrX += ally.getLocation().x;
            avrY += ally.getLocation().y;
        }

        MapLocation avrPos = new MapLocation(avrX / (allies.length + 1), avrY / (allies.length + 1));

        //If too far ahead, don't move.
        if (distance(pos, target) > distance(avrPos, target) + 2);
        //If far from pack, move towards them.
        else if (distance(pos, avrPos) > 3) moveTowards(rc, avrPos);
        else moveTowards(rc, target);

        //If within vision distance of target and there are no enemies, swap target.
        packStatus = allies;
        attack(rc);
    }

    private static void scout(RobotController rc) throws GameActionException {
        //TODO: Uncomment
//        lookForWells(rc);

        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == RobotType.HEADQUARTERS) {
                withinOppHQRange = true;
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
                        newKnownHQ = enemy.getLocation();
                        reportingHQ = true;
                        System.out.println("Spotted HQ " + enemy.getLocation());
                        break;
                    }
                }
                return;
            }
        }

        withinOppHQRange = false;
    }

    // TODO: Migrate to new well system
//    private static void lookForWells(RobotController rc) throws GameActionException {
//        //Well scouting
//        // when we discover a nearby well, make sure it is the right type and not already stored before we write it
//
//        boolean foundAll = true;
//
//        //Make sure we haven't found every well yet.
//        for (int i = WELL_INDEX_MIN; i < WELL_INDEX_MAX; i++) {
//            if (rc.readSharedArray(i) == 0) {
//                foundAll = false;
//                break;
//            }
//        }
//
//        if (foundAll) return;
//
//        WellInfo[] wells = rc.senseNearbyWells();
//
//        if (wells.length > 0) {
//            // make a location list of all stored wells of each type
//            ArrayList<MapLocation> adWellLocations = new ArrayList<>();
//            ArrayList<MapLocation> mnWellLocations = new ArrayList<>();
//            for (int i = WELL_INDEX_MIN; i <= WELL_INDEX_MAX; i++) {
//                int read = rc.readSharedArray(i);
//                if (ResourceType.values()[read / 10000] == ResourceType.ADAMANTIUM) adWellLocations.add(intToLoc(read % 10000));
//                else if (ResourceType.values()[read / 10000] == ResourceType.MANA) mnWellLocations.add(intToLoc(read % 10000));
//            }
//
//            // we only want to store numWellsStored/2 wells per type, not elixir yet
//            if (adWellLocations.size() < NUM_WELLS_STORED / 2 || mnWellLocations.size() < NUM_WELLS_STORED / 2) {
//                // check if any wells we found are new and not stored
//                for (WellInfo well : wells) {
//                    MapLocation loc = well.getMapLocation();
//                    ResourceType type = well.getResourceType();
//                    if ((type == ResourceType.MANA && mnWellLocations.size() < NUM_WELLS_STORED / 2 && !mnWellLocations.contains(loc))
//                            || (type == ResourceType.ADAMANTIUM && adWellLocations.size() < NUM_WELLS_STORED / 2 && !adWellLocations.contains(loc))) {
//                        targetWellLocation = loc;
//                        targetWellType = type;
//                        // otherwise, return to hq to report
//                        reportingWell = true;
//                        lstate = LauncherState.REPORTING;
//                        return;
//                    }
//                }
//            }
//        }
//    }

    private static void attack(RobotController rc) throws GameActionException {
        int targetPrio;
        if (rc.isActionReady() && enemies.length > 0) {
            targetPrio = launcherPriority.indexOf(enemies[0].getType());
            int targetHealth = enemies[0].getHealth();
            MapLocation targetLoc = enemies[0].location;
            for (RobotInfo enemy : enemies) {
                MapLocation loc = enemy.location;
                if (!enemy.getType().equals(RobotType.HEADQUARTERS) && rc.canActLocation(loc)) {
                    RobotType etype = enemy.getType();
                    int eprio = launcherPriority.indexOf(etype);
                    if (eprio < targetPrio || (eprio <= targetPrio && enemy.getHealth() < targetHealth)) {
                        targetHealth = enemy.getHealth();
                        targetPrio = launcherPriority.indexOf(etype);
                        targetLoc = loc;
                    }
                }
            }
            if (rc.canSenseRobotAtLocation(targetLoc) && !rc.senseRobotAtLocation(targetLoc).type.equals(RobotType.HEADQUARTERS) && rc.canAttack(targetLoc)) {
                rc.attack(targetLoc);
                if (rc.isMovementReady() && rc.canSenseRobotAtLocation(targetLoc) && rc.senseRobotAtLocation(targetLoc).type.equals(RobotType.LAUNCHER)) {
                    moveAway(rc, targetLoc);
                }
            }
        }
    }
}
