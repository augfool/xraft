package in.xnnyygn.xraft.kvstore.server;

import in.xnnyygn.xraft.core.node.*;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// TODO configs
public class ServerLauncher {

    private static final Logger logger = LoggerFactory.getLogger(ServerLauncher.class);
    private static final String MODE_STANDALONE = "standalone";
    private static final String MODE_STANDBY = "standby";
    private static final String MODE_GROUP_MEMBER = "group-member";

    private volatile Server server;

    private void execute(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(Option.builder("m")
                .hasArg()
                .argName("mode")
                .desc("start mode, available: standalone, standby, group-member. default is standalone")
                .build());
        options.addOption(Option.builder("i")
                .longOpt("id")
                .hasArg()
                .argName("node-id")
                .required()
                .desc("node id, required. must be unique in group. " +
                        "if starts with mode group-member, please ensure id in group config")
                .build());
        options.addOption(Option.builder("h")
                .hasArg()
                .argName("host")
                .desc("host, required when starts with standalone or standby mode")
                .build());
        options.addOption(Option.builder("p1")
                .longOpt("port-raft-server")
                .hasArg()
                .argName("port")
                .type(Number.class)
                .desc("port of raft server, required when starts with standalone or standby mode")
                .build());
        options.addOption(Option.builder("p2")
                .longOpt("port-service")
                .hasArg()
                .argName("port")
                .type(Number.class)
                .required()
                .desc("port of service, required")
                .build());
        options.addOption(Option.builder("d")
                .hasArg()
                .argName("data-dir")
                .desc("data directory, optional. must be present")
                .build());
        options.addOption(Option.builder("gc")
                .hasArgs()
                .argName("node-config")
                .desc("group config, required when starts with group-member mode. format: <node-config> <node-config>..., " +
                        "format of node-config: <node-id>,<host>,<port-raft-server>, eg: A,localhost,8000 B,localhost,8010")
                .build());

        if (args.length == 0) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("xraft-kvstore-server [OPTION]...", options);
            return;
        }

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmdLine = parser.parse(options, args);
            String mode = cmdLine.getOptionValue('m', MODE_STANDALONE);
            switch (mode) {
                case MODE_STANDBY:
                    startAsStandaloneOrStandby(cmdLine, true);
                    break;
                case MODE_STANDALONE:
                    startAsStandaloneOrStandby(cmdLine, false);
                    break;
                case MODE_GROUP_MEMBER:
                    startAsGroupMember(cmdLine);
                    break;
                default:
                    throw new IllegalArgumentException("illegal mode [" + mode + "]");
            }
        } catch (ParseException | IllegalArgumentException e) {
            System.err.println(e.getMessage());
        }
    }

    private void startAsStandaloneOrStandby(CommandLine cmdLine, boolean standby) throws Exception {
        if (!cmdLine.hasOption("p1") || !cmdLine.hasOption("p2")) {
            throw new IllegalArgumentException("port-raft-server or port-service required");
        }

        String id = cmdLine.getOptionValue('i');
        String host = cmdLine.getOptionValue('h', "localhost");
        int portRaftServer = ((Long) cmdLine.getParsedOptionValue("p1")).intValue();
        int portService = ((Long) cmdLine.getParsedOptionValue("p2")).intValue();

        NodeEndpoint nodeEndpoint = new NodeEndpoint(id, host, portRaftServer);
        Node node = new NodeBuilder2(nodeEndpoint.getId(), new NodeGroup(nodeEndpoint))
                .setStandby(standby)
                .setDataDir(cmdLine.getOptionValue('d'))
                .build();
        Server server = new Server(node, portService);
        logger.info("start with mode {}, id {}, host {}, port raft server {}, port service {}",
                (standby ? "standby" : "standalone"), id, host, portRaftServer, portService);
        startServer(server);
    }

    private void startAsGroupMember(CommandLine cmdLine) throws Exception {
        if (!cmdLine.hasOption("gc")) {
            throw new IllegalArgumentException("group-config required");
        }

        String[] rawGroupConfig = cmdLine.getOptionValues("gc");
        String rawNodeId = cmdLine.getOptionValue('i');
        int portService = ((Long) cmdLine.getParsedOptionValue("p2")).intValue();

        Set<NodeEndpoint> nodeEndpoints = Stream.of(rawGroupConfig)
                .map(this::parseNodeConfig)
                .collect(Collectors.toSet());

        NodeGroup nodeGroup = new NodeGroup(nodeEndpoints);
        Node node = new NodeBuilder2(new NodeId(rawNodeId), nodeGroup)
                .setDataDir(cmdLine.getOptionValue('d'))
                .build();
        Server server = new Server(node, portService);
        logger.info("start as group member, group config {}, id {}, port service {}", nodeEndpoints, rawNodeId, portService);
        startServer(server);
    }

    private NodeEndpoint parseNodeConfig(String rawNodeConfig) {
        String[] pieces = rawNodeConfig.split(",");
        if (pieces.length != 3) {
            throw new IllegalArgumentException("illegal server config [" + rawNodeConfig + "]");
        }
        String nodeId = pieces[0];
        String host = pieces[1];
        int port;
        try {
            port = Integer.parseInt(pieces[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("illegal port in node config [" + rawNodeConfig + "]");
        }
        return new NodeEndpoint(nodeId, host, port);
    }

    private void startServer(Server server) throws Exception {
        this.server = server;
        this.server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopServer, "shutdown"));
    }

    private void stopServer() {
        try {
            server.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        ServerLauncher launcher = new ServerLauncher();
        launcher.execute(args);
    }

}