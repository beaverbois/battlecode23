package Sprint2;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static Sprint2.CarrierSync.*;
import static Sprint2.LauncherSync.*;
import static Sprint2.RobotPlayer.*;
import static Sprint2.Util.*;

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

    static RobotInfo[] nearbyRobots, enemies, allies;

    static MapLocation pos;

    static MapLocation targetOppHQ;
    static int oppHQStatus = 0;

    static int targetEnemy = 0;

    static int numInPack = 8;

    //Wells
    static boolean reportingWell = false, reportingEnemy = false, reportingHQ = false;
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

        //Rushers are suppressing our HQ
        rc.setIndicatorString("HQ: " + allOpposingHQ[0]);
        readOppHeadquarters(rc, allOpposingHQ);

        nearbyRobots = rc.senseNearbyRobots();
        List<RobotInfo> alliesL = new ArrayList<>();
        List<RobotInfo> enemiesL = new ArrayList<>();

        for(RobotInfo bot : nearbyRobots) {
            if(bot.team == rc.getTeam() && bot.type == RobotType.LAUNCHER) alliesL.add(bot);
            else enemiesL.add(bot);
        }

        allies = new RobotInfo[alliesL.size()];
        enemies = new RobotInfo[enemiesL.size()];

        allies = alliesL.toArray(allies);
        enemies = enemiesL.toArray(enemies);
    }

    private static void rushing(RobotController rc) throws GameActionException {
        turnStart(rc);

        attack(rc);

        scout(rc);
        if(lstate == LauncherState.REPORTING) return;

        MapLocation t = closestTargetHQ(rc, allOpposingHQ);

        if(t != null) {
            lstate = LauncherState.SWARMING;
            targetOppHQ = t;
            moveTowards(rc, targetOppHQ);
            return;
        }

        if (rc.isMovementReady()) {
            //If an enemy launcher is seen, move some units towards it.
            //Trying moving towards the center of all HQ, then moving out as a pack.
            int centerX = 0, centerY = 0;
            for(int i = 0; i < allHQ.length; i++) {
                centerX += allHQ[i].x;
                centerY += allHQ[i].y;
            }
            MapLocation hqCenter = new MapLocation(centerX / allHQ.length, centerY / allHQ.length);
            if(allies.length < numInPack && distance(pos, hqCenter) > 2) moveTowards(rc, hqCenter);
            //If you have enough in the pack, have everyone move towards the other side.
            else if(allies.length >= numInPack) {
                MapLocation target = new MapLocation(rc.getMapWidth() - hqCenter.x, rc.getMapHeight() - hqCenter.y);
                moveTowards(rc, target);
            }


//            MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
//            MapLocation target = new MapLocation(120, 120);
//            int tooClose = 4;
//            int minDist = distance(pos, target);
//            for(int i = 32; i < 44; i++) {
//                int read = rc.readSharedArray(i);
//                MapLocation loc = intToLoc(read);
//                int dist = distance(pos, loc);
//                if(read != 0 && dist < tooClose && enemies.length < 2) {
//                    lstate = LauncherState.REPORTING;
//                    targetEnemy = read;
//                    reportingEnemy = true;
//                    moveTowards(rc, headquarters);
//                    return;
//                }
//                else if(read != 0 && dist < minDist) {
//                    //If it's by an oppHQ, don't bother.
//                    boolean close = false;
//                    for(int j = 0; j < allHQ.length; j++) {
//                        if(distance(loc, allOpposingHQ[j]) < tooClose) {
//                            close = true;
//                            break;
//                        }
//                    }
//
//                    if(!close) {
//                        //Close-ish to the target, go for it.
//                        target = loc;
//                        minDist = dist;
//                    }
//                }
//            }
//
//            //Go towards target
//            if(distance(pos, target) < 40) {
//                rc.setIndicatorString("Rushing " + target);
//                moveTowards(rc, target);
//                return;
//            }
//
//            if(reportingWell && enemies.length == 0) {
//                lstate = LauncherState.REPORTING;
//                return;
//            }
//
//            int radius = rc.getType().visionRadiusSquared;
//            Team ally = rc.getTeam();
//            RobotInfo[] allies = rc.senseNearbyRobots(radius, ally);
//
//            //Trying running to the center
//            if(distance(pos, center) < 3 && allies.length > 3) moveTowards(rc, new MapLocation(rc.getMapWidth() - headquarters.x, rc.getMapHeight() - headquarters.y));
//            else moveTowards(rc, center);
        }
    }

    private static void reporting(RobotController rc) throws GameActionException {
        turnStart(rc);

        attack(rc);

        if (rc.canWriteSharedArray(0, 0)) {
            if(reportingHQ) {
                reportHQ(rc, allOpposingHQ);
                reportingHQ = false;
            }

            if(reportingEnemy) {
                reportEnemy(rc, intToLoc(targetEnemy));
                reportingEnemy = false;
            }

            if(reportingWell) {
                reportWell(rc, targetWellLocation, targetWellType);
                reportingWell = false;
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
        lookForWells(rc);

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
                        reportingHQ = true;
                        System.out.println("Spotted HQ " + enemy.getLocation());
                        break;
                    }
                }
                return;
            }
        }
    }

    private static void lookForWells(RobotController rc) throws GameActionException {
        //Well scouting
        // when we discover a nearby well, make sure it is the right type and not already stored before we write it

        boolean foundAll = true;

        //Make sure we haven't found every well yet.
        for(int i = wellIndexMin; i < wellIndexMax; i++) {
            if(rc.readSharedArray(i) == 0) {
                foundAll = false;
                break;
            }
        }

        if(foundAll) return;

        WellInfo[] wells = rc.senseNearbyWells();

        if (wells.length > 0) {
            // make a location list of all stored wells of each type
            ArrayList<MapLocation> adWellLocations = new ArrayList<>();
            ArrayList<MapLocation> mnWellLocations = new ArrayList<>();
            for (int i = wellIndexMin; i <= wellIndexMax; i++) {
                int read = rc.readSharedArray(i);
                if (ResourceType.values()[read / 10000] == ResourceType.ADAMANTIUM) adWellLocations.add(intToLoc(read % 10000));
                else if (ResourceType.values()[read / 10000] == ResourceType.MANA) mnWellLocations.add(intToLoc(read % 10000));
            }

            // we only want to store numWellsStored/2 wells per type, not elixir yet
            if (adWellLocations.size() < NUM_WELLS_STORED / 2 || mnWellLocations.size() < NUM_WELLS_STORED / 2) {
                // check if any wells we found are new and not stored
                for (WellInfo well : wells) {
                    MapLocation loc = well.getMapLocation();
                    ResourceType type = well.getResourceType();
                    if ((type == ResourceType.MANA && mnWellLocations.size() < numWellsStored / 2 && !mnWellLocations.contains(loc))
                            || (type == ResourceType.ADAMANTIUM && adWellLocations.size() < numWellsStored / 2 && !adWellLocations.contains(loc))) {
                        targetWellLocation = loc;
                        targetWellType = type;
                        // otherwise, return to hq to report
                        reportingWell = true;
                        lstate = LauncherState.REPORTING;
                        return;
                    }
                }
            }
        }
    }

    private static void attack(RobotController rc) throws GameActionException {
        int targetPrio;
        if (rc.isActionReady() && enemies.length > 0) {
            targetPrio = launcherPriority.indexOf(enemies[0].getType());
            int targetHealth = enemies[0].getHealth();
            MapLocation target = enemies[0].location;
            for (RobotInfo enemy : enemies) {
                MapLocation loc = enemy.location;
                if(enemy.getType() != RobotType.HEADQUARTERS && rc.canActLocation(loc)) {
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
                if(rc.isMovementReady() && lstate == LauncherState.RUSHING) {
                    moveAway(rc, target);
                }
            }
        }
    }
}
