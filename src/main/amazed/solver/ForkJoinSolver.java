package amazed.solver;

import amazed.maze.Maze;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

/**
 * <code>ForkJoinSolver</code> implements a solver for
 * <code>Maze</code> objects using a fork/join multi-thread
 * depth-first search.
 * <p>
 * Instances of <code>ForkJoinSolver</code> should be run by a
 * <code>ForkJoinPool</code> object.
 */

public class ForkJoinSolver
        extends SequentialSolver {
    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal.
     *
     * @param maze the maze to be searched
     */
    // TODO, added these seemingly thread-safe data structures based on e-mail.
    public Set<Integer> visited = new ConcurrentSkipListSet<>(); // fr TA.
    public ConcurrentMap<Integer, Integer> predecessor = new ConcurrentHashMap<>(); // from demo.
    public Deque<Integer> frontier = new ConcurrentLinkedDeque<>(); // from demo.

    public ForkJoinSolver(Maze maze) {
        super(maze);
    }

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal, forking after a given number of visited
     * nodes.
     *
     * @param maze      the maze to be searched
     * @param forkAfter the number of steps (visited nodes) after
     *                  which a parallel task is forked; if
     *                  <code>forkAfter &lt;= 0</code> the solver never
     *                  forks new tasks
     */
    public ForkJoinSolver(Maze maze, int forkAfter) {
        this(maze);
        this.forkAfter = forkAfter;
    }

    /**
     * Searches for and returns the path, as a list of node
     * identifiers, that goes from the start node to a goal node in
     * the maze. If such a path cannot be found (because there are no
     * goals, or all goals are unreacheable), the method returns
     * <code>null</code>.
     *
     * @return the list of node identifiers from the start node to a
     *         goal node in the maze; <code>null</code> if such a path cannot
     *         be found.
     */
    @Override
    public List<Integer> compute() {
        return parallelSearch();
    }

    private List<Integer> parallelSearch() {
        // all nodes explored no goal found, return null
        List<Integer> res = null;

        // if two new paths avaliable, create new threads to explore each

        // mostly copied from sequentialSolver dfs
        int splitAt = start;
        List<ForkJoinSolver> branches = new ArrayList<>();

        // one player active on the maze at start
        int player = maze.newPlayer(start);
        // start with start node
        frontier.push(start);
        // as long as not all nodes have been processed
        while (!frontier.isEmpty()) {
            // get the new node to process
            int current = frontier.pop();

            // reached goal?
            if (maze.hasGoal(current)) {
                // move player to goal
                maze.move(player, current);
                // search finished: reconstruct and return path
                res = pathFromTo(start, current);
                break;
            }

            // if current node has not been visited yet,
            // changed to .add(current) in orer to NOT have been visited by any other thread
            // yet.
            // should prevent two threads from processing the same node?
            // ^^ prevents race condition where two treads try to visit the same node before
            // one of them marking node as visited
            if (visited.add(current)) {
                // move player to current node
                maze.move(player, current);
                // mark node as visited
                Set<Integer> nbs = maze.neighbors(current);
                // any neighbor already visited are removed
                nbs.removeIf((nb) -> visited.contains(nb));
                // if nb has not been already visited,
                // nb can be reached from current (i.e., current is nb's predecessor)
                nbs.forEach((nb) -> predecessor.put(nb, current));

                if (nbs.isEmpty()) { // no neighbors

                } else if (nbs.size() > 1) { // fork in the road
                    splitAt = current;

                    // fork for each other unvisited neighbor
                    for (int nb : nbs) {
                        ForkJoinSolver branch = new ForkJoinSolver(this.maze);
                        // new fork starts at this neighbor
                        branch.start = nb;
                        // same set of visited tiles, if ANY branch have visited a tile then no branch
                        // should visit it
                        branch.visited = this.visited;
                        // copy over the predecessors
                        // branch.predecessor.putAll(this.predecessor);
                        // add this branch to list of branches coming off of this main
                        branches.add(branch);

                        branch.fork();
                    }
                } else { // only one path, continue with this thread
                    frontier.push(nbs.iterator().next()); // ''first'' element in nbs
                }
            }
        }

        for (ForkJoinSolver branch : branches) {
            List<Integer> branchRes = branch.join();
            if (branchRes != null) {// if branch not result in null
                res = pathFromTo(start, splitAt);
                res.addAll(branchRes);
                break;
            }
        }
        return res;
    }

    // since a new predecessor is used this method needs to be redefined so that it
    // is not the predecessor in the super-class that is used. 
    @Override
    protected List<Integer> pathFromTo(int from, int to) {
        List<Integer> path = new LinkedList<>();
        Integer current = to;
        while (current != from) {
            path.add(current);
            current = predecessor.get(current);
            if (current == null)
                return null;
        }
        path.add(from);
        Collections.reverse(path);
        return path;
    }
    /*
     *** REQUIREMENTS // from description
     * * returns path as list of integers (id) if successfull, if not returns null.
     * nodes visited at most once
     * * must terminate, regardless of number of processors avaliable
     * * must to function with arbitrary mazes, without goals or more with more than
     * one goal, accepting any of the goals
     * * must explore from adjacent nodes, not randomly pick and ''guess''.
     * * must use javas fork/join parallelism
     * * at some point must have at least two threads active at once
     * * must not contain race conditions or data races
     * * must be lock free (no semaphores, locks, or synchronized blocks)
     */
}
