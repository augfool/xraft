package in.xnnyygn.xraft.kvstore;

import in.xnnyygn.xraft.core.nodestate.NodeRole;
import in.xnnyygn.xraft.core.service.Channel;
import in.xnnyygn.xraft.core.service.RedirectException;
import in.xnnyygn.xraft.core.service.NodeStateException;
import in.xnnyygn.xraft.kvstore.command.GetCommand;
import in.xnnyygn.xraft.kvstore.command.SetCommand;

public class EmbeddedChannel implements Channel {

    private final Server server;

    public EmbeddedChannel(Server server) {
        this.server = server;
    }

    @Override
    public Object send(Object payload) {
//        try {
//            if (payload instanceof SetCommand) {
//                SetCommand command = (SetCommand) payload;
//                this.server.set(command.getKey(), command.getValue());
//                return null;
//            }
//            if (payload instanceof GetCommand) {
//                GetCommand command = (GetCommand) payload;
//                return this.server.get(command.getKey());
//            }
//            throw new IllegalArgumentException("unexpected payload type " + payload.getClass());
//        } catch (NodeStateException e) {
//            if (e.getRole() == NodeRole.FOLLOWER && e.isLeaderIdPresent()) {
//                throw new RedirectException(e.getLeaderId());
//            }
//            throw e;
//        }
        return null;
    }

}