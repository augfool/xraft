package in.xnnyygn.xraft.actor;

import akka.actor.AbstractActor;
import in.xnnyygn.xraft.messages.AppendEntriesRpcMessage;
import in.xnnyygn.xraft.messages.RequestVoteResultMessage;
import in.xnnyygn.xraft.messages.RequestVoteRpcMessage;
import in.xnnyygn.xraft.messages.SimpleMessage;
import in.xnnyygn.xraft.rpc.AppendEntriesRpc;
import in.xnnyygn.xraft.rpc.RequestVoteResult;
import in.xnnyygn.xraft.rpc.RequestVoteRpc;
import in.xnnyygn.xraft.rpc.Router;
import in.xnnyygn.xraft.schedule.ElectionTimeout;
import in.xnnyygn.xraft.schedule.LogReplicationTask;
import in.xnnyygn.xraft.schedule.Scheduler;
import in.xnnyygn.xraft.server.ServerGroup;
import in.xnnyygn.xraft.server.ServerId;
import in.xnnyygn.xraft.server.ServerStore;
import in.xnnyygn.xraft.serverstate.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElectionActor extends AbstractActor implements ServerStateContext {

    private static final Logger logger = LoggerFactory.getLogger(ElectionActor.class);

    private AbstractServerState serverState;

    private final ServerGroup serverGroup;
    private final ServerId selfServerId;
    private final ServerStore serverStore;
    private final Router rpcRouter;

    private final Scheduler scheduler;

    public ElectionActor(ServerGroup serverGroup, ServerId selfServerId, ServerStore serverStore, Router rpcRouter) {
        super();
        this.serverGroup = serverGroup;
        this.selfServerId = selfServerId;
        this.serverStore = serverStore;
        this.rpcRouter = rpcRouter;

        this.scheduler = new Scheduler(selfServerId);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(SimpleMessage.class, msg -> {
            switch (msg.getKind()) {
                case START_UP:
                    startUp();
                    break;
                case ELECTION_TIMEOUT:
                    onElectionTimeout();
                    break;
            }
        }).match(RequestVoteRpcMessage.class, msg -> {
            onReceiveRequestVoteRpc(msg.getRpc());
        }).match(RequestVoteResultMessage.class, msg -> {
            onReceiveRequestVoteResult(msg.getResult(), msg.getSenderServerId());
        }).match(AppendEntriesRpcMessage.class, msg -> {
            onReceiveAppendEntriesRpc(msg.getRpc());
        }).build();
    }

    @Override
    public void postStop() throws Exception {
        logger.debug("Server {}, stop scheduler", this.selfServerId);
        this.scheduler.stop();
    }

    private void startUp() {
        this.serverState = new FollowerServerState(this.serverStore, this.scheduleElectionTimeout());
        logger.debug("Server {}, start with state {}", this.selfServerId, this.serverState);
        serverStateChanged(this.serverState.takeSnapshot());
    }

    private void onElectionTimeout() {
        logger.debug("Server {}, election timeout", this.selfServerId);
        this.serverState.onElectionTimeout(this);
    }

    private void replicateLog() {
        logger.debug("Server {}, replicate log", this.selfServerId);
        AppendEntriesRpc rpc = new AppendEntriesRpc();
        rpc.setTerm(this.serverState.getTerm());
        rpc.setLeaderId(this.selfServerId);
        getRpcRouter().sendRpc(rpc);
    }

    private void onReceiveRequestVoteResult(RequestVoteResult result, ServerId senderServerId) {
        logger.debug("Server {}, receive {} from peer {}", this.selfServerId, result, senderServerId);
        this.serverState.onReceiveRequestVoteResult(this, result);
    }

    private void onReceiveRequestVoteRpc(RequestVoteRpc rpc) {
        logger.debug("Server {}, receive {} from peer {}", this.selfServerId, rpc, rpc.getCandidateId());
        this.serverState.onReceiveRequestVoteRpc(this, rpc);
    }

    private void onReceiveAppendEntriesRpc(AppendEntriesRpc rpc) {
        logger.debug("Server {}, receive {} from leader {}", this.selfServerId, rpc, rpc.getLeaderId());
        this.serverState.onReceiveAppendEntriesRpc(this, rpc);
    }

    @Override
    public ServerId getSelfServerId() {
        return this.selfServerId;
    }

    @Override
    public int getServerCount() {
        return this.serverGroup.getServerCount();
    }

    @Override
    public void setServerState(AbstractServerState serverState) {
        logger.debug("Server {}, state changed {} -> {}", this.selfServerId, this.serverState, serverState);
        this.serverState = serverState;
        serverStateChanged(this.serverState.takeSnapshot());
    }

    @Override
    public LogReplicationTask scheduleLogReplicationTask() {
        return this.scheduler.scheduleLogReplicationTask(this::replicateLog);
    }

    @Override
    public Router getRpcRouter() {
        return this.rpcRouter;
    }

    @Override
    public ElectionTimeout scheduleElectionTimeout() {
        return this.scheduler.scheduleElectionTimeout(this::onElectionTimeout);
    }

    ///////////////

    private ServerStateSnapshot lastServerState;

    private void serverStateChanged(ServerStateSnapshot snapshot) {
        if (lastServerState == null || !isStable(lastServerState, snapshot)) {
            logger.info("Server {}, state changed -> {}", this.selfServerId, snapshot);
            lastServerState = snapshot;
        }
    }

    private boolean isStable(ServerStateSnapshot stateBefore, ServerStateSnapshot stateAfter) {
        return stateBefore.getRole() == ServerRole.FOLLOWER &&
                stateAfter.getRole() == stateBefore.getRole() &&
                stateAfter.getTerm() == stateBefore.getTerm() &&
                stateAfter.getLeaderId() == stateBefore.getLeaderId() &&
                stateAfter.getVotedFor() == stateBefore.getVotedFor();
    }

}
