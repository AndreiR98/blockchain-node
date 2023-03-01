package uk.co.roteala.glaciernode.node;

import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import uk.co.roteala.glaciernode.storage.GlacierStorage;

import java.util.Arrays;

@Component
@Slf4j
public class Node {
    @Autowired
    private GlacierStorage storage;

    @Bean
    public void nodeStart() {
        String[] peersAddress = null;

        //TODO:Move to commons
        //Storage configuraiton
        DataStorageConfiguration storage = new DataStorageConfiguration()
                .setStoragePath("");

        storage.getDefaultDataRegionConfiguration().setPersistenceEnabled(true);

        TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder()
                .setAddresses(Arrays.asList(peersAddress));

        TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi()
                .setIpFinder(ipFinder);

        IgniteConfiguration configuration = new IgniteConfiguration()
                .setDiscoverySpi(discoverySpi);

        Ignition.start(configuration);
    }
}
