package in.xnnyygn.xraft.serverstate;

import in.xnnyygn.xraft.rpc.AppendEntriesResult;
import in.xnnyygn.xraft.rpc.AppendEntriesRpc;
import in.xnnyygn.xraft.rpc.RequestVoteResult;
import in.xnnyygn.xraft.rpc.RequestVoteRpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract server state.
 */
public abstract class AbstractServerState {

    private static final Logger logger = LoggerFactory.getLogger(AbstractServerState.class);
    protected final ServerRole role;
    protected final int term;

    /**
     * Create.
     *
     * @param role role
     * @param term term
     */
    AbstractServerState(ServerRole role, int term) {
        this.role = role;
        this.term = term;
    }

    /**
     * Get role.
     *
     * @return role
     */
    public ServerRole getRole() {
        return role;
    }

    public abstract ServerStateSnapshot takeSnapshot();

    /**
     * Get term.
     *
     * @return term
     */
    public int getTerm() {
        return term;
    }

    /**
     * Cancel timeout or task of current state.
     */
    protected abstract void cancelTimeoutOrTask();

    /**
     * Called when election timeout.
     *
     * @param context context
     */
    public void onElectionTimeout(ServerStateContext context) {
        if (this.role == ServerRole.LEADER) {
            logger.warn("Server {}, current role is LEADER, ignore", context.getSelfServerId());
            return;
        }

        // follower: start election
        // candidate: restart election
        int newTerm = this.term + 1;

        // reset election timeout
        this.cancelTimeoutOrTask();
        context.setServerState(new CandidateServerState(newTerm, context.scheduleElectionTimeout()));

        // rpc
        RequestVoteRpc rpc = new RequestVoteRpc();
        rpc.setTerm(newTerm);
        rpc.setCandidateId(context.getSelfServerId());
        context.getRpcRouter().sendRpc(rpc);
    }

    /**
     * Called when receive request vote result.
     *
     * @param context context
     * @param result  result
     */
    public abstract void onReceiveRequestVoteResult(ServerStateContext context, RequestVoteResult result);

    /**
     * Called when receive request vote rpc.
     *
     * @param context context
     * @param rpc     rpc
     */
    public void onReceiveRequestVoteRpc(ServerStateContext context, RequestVoteRpc rpc) {
        RequestVoteResult result;

        if (rpc.getTerm() < this.term) {

            // peer's term is old
            result = new RequestVoteResult(this.term, false);
        } else if (rpc.getTerm() == this.term) {
            result = processRequestVoteRpc(context, rpc);
        } else {

            // peer's term > current term
            logger.debug("Server {}, update to peer {}'s term {} and vote for it", context.getSelfServerId(), rpc.getCandidateId(), rpc.getTerm());
            this.cancelTimeoutOrTask();
            context.setServerState(new FollowerServerState(rpc.getTerm(), rpc.getCandidateId(), null, context.scheduleElectionTimeout()));
            result = new RequestVoteResult(rpc.getTerm(), true);
        }

        context.getRpcRouter().sendResult(result, rpc.getCandidateId());
    }

    /**
     * Process request vote rpc in same term.
     *
     * @param context context
     * @param rpc     rpc
     * @return request vote result
     */
    protected abstract RequestVoteResult processRequestVoteRpc(ServerStateContext context, RequestVoteRpc rpc);

    /**
     * Called when receive append entries rpc.
     *
     * @param context context
     * @param rpc     rpc
     */
    public void onReceiveAppendEntriesRpc(ServerStateContext context, AppendEntriesRpc rpc) {
        AppendEntriesResult result;

        if (rpc.getTerm() < this.term) {

            // peer's term is old
            result = new AppendEntriesResult(this.term, false);
        } else if (rpc.getTerm() == this.term) {
            result = processAppendEntriesRpc(context, rpc);
        } else {

            // leader's term > current term
            this.cancelTimeoutOrTask();
            context.setServerState(new FollowerServerState(rpc.getTerm(), null, rpc.getLeaderId(), context.scheduleElectionTimeout()));
            result = new AppendEntriesResult(rpc.getTerm(), true);
        }

        context.getRpcRouter().sendResult(result, rpc.getLeaderId());
    }

    /**
     * Process append entries rpc in same term.
     *
     * @param context context
     * @param rpc     rpc
     * @return append entries result
     */
    protected abstract AppendEntriesResult processAppendEntriesRpc(ServerStateContext context, AppendEntriesRpc rpc);

}
