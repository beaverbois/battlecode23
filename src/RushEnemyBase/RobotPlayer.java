package RushEnemyBase;

import battlecode.common.*;

import java.util.*;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public strictfp class RobotPlayer {

    /**
     * We will use this variable to count the number of turns this robot has been alive.
     * You can use static variables like this to save any information you want. Keep in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between your robots.
     */
    static int turnCount = 0;

    /**
     * A random number generator.
     * We will use this RNG to make some random moves. The Random class is provided by the java.util.Random
     * import at the top of this file. Here, we *seed* the RNG with a constant number (6147); this makes sure
     * we get the same sequence of numbers every time this code is run. This is very useful for debugging!
     */
    static final Random rng = new Random(6147);

    /** Array containing all the possible movement directions. */
    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    static final ArrayList<RobotType> launcherPriority = new ArrayList<RobotType>(Arrays.asList(
            RobotType.DESTABILIZER,
            RobotType.LAUNCHER,
            RobotType.BOOSTER,
            RobotType.CARRIER,
            RobotType.AMPLIFIER,
            RobotType.HEADQUARTERS
    ));

    static enum CarrierState {
        FARMING,
        RETURNING,
        SCOUTING,
        REPORTING
    }
    static CarrierState cstate = CarrierState.FARMING;

    static MapLocation headquarters = new MapLocation(0, 0);
    static MapLocation[] allHQ;
    static MapLocation[] allOpposingHQ;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * It is like the main function for your robot. If this method returns, the robot dies!
     *
     * @param rc  The RobotController object. You use it to perform actions from this robot, and to get
     *            information on its current status. Essentially your portal to interacting with the world.
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // Hello world! Standard output is very useful for debugging.
        // Everything you say here will be directly viewable in your terminal when you run a match!
        //System.out.println("I'm a " + rc.getType() + " and I just got created! I have health " + rc.getHealth());

        // You can also use indicators to save debug notes in replays.
        rc.setIndicatorString("Hello world!");

        //Will need to fix this later
        if(rc.getType() == RobotType.HEADQUARTERS) {
            headquarters = rc.getLocation();
            int numHQ = rc.readSharedArray(0);
            rc.writeSharedArray(0, ++numHQ);
            //Write position in #HQ index as x * 60 + y.
            rc.writeSharedArray(numHQ, headquarters.x * 60 + headquarters.y);
        } else {
            allHQ = new MapLocation[rc.readSharedArray(0)];
            allOpposingHQ = new MapLocation[allHQ.length];
            for(int i = 0; i < allHQ.length; i++) {
                allHQ[i] = toMap(rc.readSharedArray(i+1));
                allOpposingHQ[i] = toMap(rc.readSharedArray(i+allHQ.length+1));
            }
            headquarters = closest(rc.getLocation(), allHQ);
        }

        if(rc.getType() == RobotType.CARRIER) {
            //Using last index to convey role info to new bots, can do operations to
            //compress multiple robots into one slot.
            if(rc.readSharedArray(31) == 1) cstate = CarrierState.SCOUTING;
        }

        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to do.

            turnCount += 1;  // We have now been alive for one more turn!

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode.
            try {
                // The same run() function is called for every robot on your team, even if they are
                // different types. Here, we separate the control depending on the RobotType, so we can
                // use different strategies on different robots. If you wish, you are free to rewrite
                // this into a different control structure!
                switch (rc.getType()) {
                    case HEADQUARTERS:     runHeadquarters(rc);  break;
                    case CARRIER:      runCarrier(rc);   break;
                    case LAUNCHER: runLauncher(rc); break;
                    case BOOSTER: // Examplefuncsplayer doesn't use any of these robot types below.
                    case DESTABILIZER: // You might want to give them a try!
                    case AMPLIFIER:       break;
                }

            } catch (GameActionException e) {
                // Oh no! It looks like we did something illegal in the Battlecode world. You should
                // handle GameActionExceptions judiciously, in case unexpected events occur in the game
                // world. Remember, uncaught exceptions cause your robot to explode!
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();

            } catch (Exception e) {
                // Oh no! It looks like our code tried to do something bad. This isn't a
                // GameActionException, so it's more likely to be a bug in our code.
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();

            } finally {
                // Signify we've done everything we want to do, thereby ending our turn.
                // This will make our code wait until the next turn, and then perform this loop again.
                Clock.yield();
            }
            // End of loop: go back to the top. Clock.yield() has ended, so it's time for another turn!
        }

        // Your code should never reach here (unless it's intentional)! Self-destruction imminent...
    }

    /**
     * Run a single turn for a Headquarters.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    static void runHeadquarters(RobotController rc) throws GameActionException {
        //Make scout carriers every 5 turns.
        if(turnCount % 5 == 0) rc.writeSharedArray(31, 1);
        else if(turnCount % 6 == 1) rc.writeSharedArray(31, 0);
        // Pick a direction to build in.
        int i = rng.nextInt(directions.length);
        //Direction dir = directions[i++];
        MapLocation newLoc = rc.getLocation().add(directions[i++%directions.length]);
        while(rc.canSenseRobotAtLocation(newLoc) && i < directions.length) {
            newLoc = rc.getLocation().add(directions[i++%directions.length]);
        }
        //Testing no anchors
        /*if (rc.canBuildAnchor(Anchor.STANDARD)) {
            // If we can build an anchor do it!
            rc.buildAnchor(Anchor.STANDARD);
            rc.setIndicatorString("Building anchor! " + rc.getAnchor());
        }
        */
        //Trying prioritizing launchers. Will likely want a state machine
        //to determine the ratio in which they should be built.
        if (rc.canBuildRobot(RobotType.LAUNCHER, newLoc)) {
            // Let's try to build a carrier.
            rc.setIndicatorString("Building a launcher");
            rc.buildRobot(RobotType.LAUNCHER, newLoc);
        } else if (rc.canBuildRobot(RobotType.CARRIER, newLoc)){
            // Let's try to build a launcher.
            rc.setIndicatorString("Building a carrier");
            rc.buildRobot(RobotType.CARRIER, newLoc);
        }
    }

    /**
     * Run a single turn for a Carrier.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    static void runCarrier(RobotController rc) throws GameActionException {
        //Start by defining weight and position
        int weight = rc.getResourceAmount(ResourceType.ADAMANTIUM) +
                rc.getResourceAmount(ResourceType.MANA) +
                rc.getResourceAmount(ResourceType.ELIXIR);
        MapLocation me = rc.getLocation();

        if(weight == 40 && cstate == CarrierState.FARMING) cstate = CarrierState.RETURNING;

        int radius = rc.getType().actionRadiusSquared;
        Team opponent = rc.getTeam().opponent();
        RobotInfo[] enemies = rc.senseNearbyRobots(radius, opponent);
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == RobotType.HEADQUARTERS) {
                int pos = fromMap(enemy.getLocation());
                int numHQ = allHQ.length;
                for (int i = 0; i < numHQ; i++) {
                    int val = fromMap(allOpposingHQ[i]);
                    if (val == pos) break;
                    if (val == 0) {
                        allOpposingHQ[i] = toMap(pos);
                        cstate = CarrierState.REPORTING;
                        break;
                    }
                }
            }
        }

        if(cstate == CarrierState.REPORTING && rc.canWriteSharedArray(0, 0)) {
            for(int i = 0; i < allHQ.length; i++) rc.writeSharedArray(allHQ.length+i+1, fromMap(allOpposingHQ[i]));
            cstate = CarrierState.FARMING;
        }

        //No anchors in the game atm
//        if (rc.getAnchor() != null) {
//            // If I have an anchor singularly focus on getting it to the first island I see
//            int[] islands = rc.senseNearbyIslands();
//            Set<MapLocation> islandLocs = new HashSet<>();
//            for (int id : islands) {
//                MapLocation[] thisIslandLocs = rc.senseNearbyIslandLocations(id);
//                islandLocs.addAll(Arrays.asList(thisIslandLocs));
//            }
//            if (islandLocs.size() > 0) {
//                MapLocation islandLocation = islandLocs.iterator().next();
//                rc.setIndicatorString("Moving my anchor towards " + islandLocation);
//                while (!rc.getLocation().equals(islandLocation)) {
//                    Direction dir = rc.getLocation().directionTo(islandLocation);
//                    if (rc.canMove(dir)) {
//                        rc.move(dir);
//                    }
//                }
//                if (rc.canPlaceAnchor()) {
//                    rc.setIndicatorString("Huzzah, placed anchor!");
//                    rc.placeAnchor();
//                }
//            }
//        }

        if (headquarters.isAdjacentTo(me) && weight > 0) {
            if (rc.canTransferResource(headquarters, ResourceType.ADAMANTIUM, 1))
                rc.transferResource(headquarters, ResourceType.ADAMANTIUM, rc.getResourceAmount(ResourceType.ADAMANTIUM));
            if (rc.canTransferResource(headquarters, ResourceType.MANA, 1))
                rc.transferResource(headquarters, ResourceType.MANA, rc.getResourceAmount(ResourceType.MANA));
            if (rc.canTransferResource(headquarters, ResourceType.ELIXIR, 1))
                rc.transferResource(headquarters, ResourceType.ELIXIR, rc.getResourceAmount(ResourceType.ELIXIR));
            return;
        } else if (headquarters.isAdjacentTo(me)) cstate = CarrierState.FARMING;

        // Occasionally try out the carriers attack
        //Removing atm
        /*
        if (rng.nextInt(20) == 1) {
            RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            if (enemyRobots.length > 0) {
                if (rc.canAttack(enemyRobots[0].location)) {
                    rc.attack(enemyRobots[0].location);
                }
            }
        }
         */

        if (cstate == CarrierState.FARMING) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    MapLocation wellLocation = new MapLocation(me.x + dx, me.y + dy);
                    if (rc.canCollectResource(wellLocation, -1)) {
                        rc.collectResource(wellLocation, -1);
                        return;
                    }
                }
            }

            // If we can see a well, move towards it
            if(rc.isMovementReady()) {
                WellInfo[] wells = rc.senseNearbyWells();
                MapLocation[] wellLoc = new MapLocation[wells.length];
                if(wells.length != 0) {
                    for (int i = 0; i < wells.length; i++) wellLoc[i] = wells[i].getMapLocation();
                    Direction dir = towards(me, closest(me, wellLoc));
                    if (rc.canMove(dir)) {
                        rc.move(dir);
                        return;
                    }
                }
            }
        }

        if (cstate == CarrierState.FARMING || cstate == CarrierState.SCOUTING && rc.isMovementReady()) {
            //Move a random direction away from headquarters.
            ArrayList<Direction> away = new ArrayList<Direction>(Arrays.asList(directions));
            if (headquarters.x > me.x) away.remove(Direction.EAST);
            else if (headquarters.x < me.x) away.remove(Direction.WEST);
            if (headquarters.y > me.y) away.remove(Direction.NORTH);
            else if (headquarters.y < me.y) away.remove(Direction.SOUTH);
            if (headquarters.x > me.x && headquarters.y > me.y) away.remove(Direction.NORTHEAST);
            else if (headquarters.x < me.x && headquarters.y > me.y) away.remove(Direction.NORTHWEST);
            else if (headquarters.x > me.x && headquarters.y < me.y) away.remove(Direction.SOUTHEAST);
            else if (headquarters.x < me.x && headquarters.y < me.y) away.remove(Direction.SOUTHWEST);
            int randDir = rng.nextInt(away.size());
            Direction dir = away.get(randDir++ % away.size());
            for (int i = 0; i < away.size() && !rc.canMove(dir); i++) {
                dir = away.get(randDir++ % away.size());
            }
            if (rc.canMove(dir)) rc.move(dir);
            else {
                randDir = rng.nextInt(directions.length);
                dir = directions[randDir++ % directions.length];
                for (int i = 0; i < away.size() && !rc.canMove(dir); i++) {
                    dir = away.get(randDir++ % away.size());
                }
                if (rc.canMove(dir)) rc.move(dir);
            }
        }

        if (cstate == CarrierState.RETURNING || cstate == CarrierState.REPORTING && rc.isMovementReady()) {
            //Move towards closest headquarters.
            headquarters = closest(me, allHQ);
            int randDir = rng.nextInt(directions.length);
            Direction dir = towards(me, headquarters);
            for (int i = 0; i < directions.length && !rc.canMove(dir); i++) {
                dir = directions[randDir++ % directions.length];
            }
            if (rc.canMove(dir)) rc.move(dir);
        }
    }

    /**
     * Run a single turn for a Launcher.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    static void runLauncher(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        for(int i = 0; i < allHQ.length; i++) allOpposingHQ[i] = toMap(rc.readSharedArray(i));

        // Try to attack someone
        int radius = rc.getType().actionRadiusSquared;
        Team opponent = rc.getTeam().opponent();
        RobotInfo[] enemies = rc.senseNearbyRobots(radius, opponent);
        int targetPrio;

        if (rc.isActionReady() && enemies.length > 0) {
            // MapLocation toAttack = enemies[0].location;
            targetPrio = launcherPriority.indexOf(enemies[0].getType());
            MapLocation target = enemies[0].location;
            for(RobotInfo enemy : enemies) {
                RobotType etype = enemy.getType();
                if(launcherPriority.indexOf(etype) < targetPrio) {
                    targetPrio = launcherPriority.indexOf(etype);
                    target = enemy.location;
                }
            }
            if (rc.canAttack(target)) {
                rc.setIndicatorString("Attacking");        
                rc.attack(target);
                if(rc.isMovementReady()) {
                    if(rc.canMove(towards(me, target))) {
                        rc.move(towards(me, target));
                    }
                }
            }
        }

        //Move towards closest enemy HQ if exists. If not, move randomly.
        if(rc.isMovementReady()) {
            if(fromMap(allOpposingHQ[0]) != 0) {
                System.out.println("Moving towards enemy HQ " + allOpposingHQ[0]);
                //We know at least one enemy HQ exists.
                int count = 1;
                for(; count < allOpposingHQ.length; count++)
                    if(fromMap(allOpposingHQ[count]) == 0) break;
                MapLocation[] knownOppHQ = new MapLocation[count];
                System.arraycopy(allOpposingHQ, 0, knownOppHQ, count-1, count);
                MapLocation closeHQ = closest(me, knownOppHQ);
                int randDir = rng.nextInt(directions.length);
                Direction dir = towards(me, closeHQ);
                for (int j = 0; j < directions.length && !rc.canMove(dir); j++) {
                    dir = directions[randDir++ % directions.length];
                }
                if (rc.canMove(dir)) rc.move(dir);
            } else {
                Direction dir = directions[rng.nextInt(directions.length)];
                if (rc.isMovementReady() && rc.canMove(dir)) {
                    rc.setIndicatorString("Moving " + dir);
                    rc.move(dir);
                }
            }
        }
    }

    static Direction towards(MapLocation pos, MapLocation target) {
        //Does not take terrain into account yet.
        int xgap, ygap;

        xgap = target.x - pos.x;
        ygap = target.y - pos.y;

        if(xgap == 0) return ygap > 0 ? Direction.NORTH : ygap == 0 ? Direction.CENTER : Direction.SOUTH;
        if(ygap == 0) return xgap > 0 ? Direction.EAST : Direction.WEST;
        if(xgap > 0) return ygap > 0 ? Direction.NORTHEAST : Direction.SOUTHEAST;
        return ygap > 0 ? Direction.NORTHWEST : Direction.SOUTHWEST;
    }

    static int distance(MapLocation pos, MapLocation target) {
        //Does not take into account impassable terrain.
        return Math.max(Math.abs(target.x - pos.x), Math.abs(target.y - pos.y));
    }

    static MapLocation closest(MapLocation pos, MapLocation... targets) {
        //Assuming at least 1 target, and ignoring terrain.
        if(targets.length == 0) {
            System.out.println("Calling closest with no targets.");
            return pos;
        }
        int minDistance = distance(pos, targets[0]);
        MapLocation close = targets[0];
        for(MapLocation target : targets) {
            if(distance(pos, target) < minDistance) {
                minDistance = distance(pos, target);
                close = target;
            }
        }
        return close;
    }

    static MapLocation toMap(int raw) {
        return new MapLocation(raw / 60, raw % 60);
    }

    //Just writing this, don't think it's worth using.
    static int fromMap(MapLocation pos) {
        return pos.x * 60 + pos.y;
    }
}
