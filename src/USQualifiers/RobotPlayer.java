package USQualifiers;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Random;

import static USQualifiers.Util.closestAvailableDirectionAroundRobot;
import static USQualifiers.Util.isJammed;

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
    static final Random rng = new Random();
    static int jammedTurns = 0;
    static MapLocation jammedLocation = null;
    static int bytecodeLimit = 0;
    static Team robotTeam;
    static Team opponentTeam;

    static String[] deathMessages = {
            "Wakanda Forever!",
            "You are worthless.",
            "The cake is a lie",
            "Winter is coming",
            "The end has come",
            "Luke I am your father",
            "To infinity and beyond!",
            "Goodbye cruel world",
            "Hello world...",
            "Well, nobody's perfect...",
            "E.T. phone home",
            "Father I am coming",
            "There's no place like home",
            "War. War changes people",
            "I have a dream...",
            "DEATH IS A PREFERABLE ALTERNATIVE TO COMMUNISM",
            "Mommy, I'm cold",
            "Help me...",
            "Mama, ooooohhh, I don't wanna die",
            "I sometimes wish I'd never been born at all",
            "I like jazz",
            "By all known laws of aviation...",
            "Never forget",
            "Sweet dreams",
            "The light, it burns!",
            "'Tis but a flesh wound.",
            "So dark...",
            "Honey, where's my super suit?",
            "'Till we meet again!"
    };

    /**
     * Array containing all the possible movement directions.
     */
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

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * It is like the main function for your robot. If this method returns, the robot dies!
     *
     * @param rc The RobotController object. You use it to perform actions from this robot, and to get
     *           information on its current status. Essentially your portal to interacting with the world.
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // Hello world! Standard output is very useful for debugging.
        // Everything you say here will be directly viewable in your terminal when you run a match!
        //System.out.println("I'm a " + rc.getType() + " and I just got created! I have health " + rc.getHealth());

        // You can also use indicators to save debug notes in replays.
        rc.setIndicatorString("Spawned");
        bytecodeLimit = (int) (rc.getType().bytecodeLimit * 0.98);
        robotTeam = rc.getTeam();
        opponentTeam = robotTeam.opponent();

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
                    case HEADQUARTERS:
                        Headquarters.run(rc);
                        break;
                    case CARRIER:
                        Carrier.run(rc);
                        break;
                    case LAUNCHER:
                        Launcher.run(rc);
                        break;
                    case BOOSTER:
                        Booster.run(rc);
                        break;
                    case DESTABILIZER:
                        Destabilizer.run(rc);
                        break;
                    case AMPLIFIER:
                        Amplifier.run(rc);
                        break;
                }

                if (rc.getType() != RobotType.HEADQUARTERS && isJammed(rc)) {
                    if (jammedTurns == 0) {
                        jammedLocation = rc.getLocation();
                    }
                    if (jammedLocation == rc.getLocation()) {
                        jammedTurns++;
                        if (jammedTurns > 1) perish(rc);
                    } else {
                        jammedTurns = 0;
                        jammedLocation = null;
                    }
                } else {
                    jammedTurns = 0;
                    jammedLocation = null;
                }

                if (Clock.getBytecodeNum() >= bytecodeLimit) {
                    System.out.println("[WARN] Bytecode Limit Exceeded!!");
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

    public static void perish(RobotController rc) throws GameActionException {
        System.out.println(deathMessages[rng.nextInt(deathMessages.length)]);
        rc.disintegrate();
    }
}