package org.eclipse.smarthome.binding.tinkerforge.internal.handler;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.binding.tinkerforge.discovery.BrickDaemonDiscoveryService;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.types.Command;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import com.tinkerforge.AlreadyConnectedException;
import com.tinkerforge.BrickDaemonConfig;
import com.tinkerforge.CryptoException;
import com.tinkerforge.IPConnection;
import com.tinkerforge.IPConnection.EnumerateListener;
import com.tinkerforge.NetworkException;
import com.tinkerforge.NotConnectedException;
import com.tinkerforge.TimeoutException;
import com.tinkerforge.TinkerforgeException;

public class BrickDaemonHandler extends BaseBridgeHandler {
    public IPConnection ipcon;
    private Consumer<BrickDaemonDiscoveryService> registerFn;
    private Consumer<BrickDaemonDiscoveryService> deregisterFn;
    private BrickDaemonDiscoveryService discoveryService;
    @Nullable
    private ScheduledFuture<?> connectFuture;

    public BrickDaemonHandler(Bridge bridge, Consumer<BrickDaemonDiscoveryService> registerFn,
            Consumer<BrickDaemonDiscoveryService> deregisterFn) {
        super(bridge);
        this.registerFn = registerFn;
        this.deregisterFn = deregisterFn;
        ipcon = new IPConnection();
        ipcon.setAutoReconnect(false);
    }

    @Override
    public void handleCommand(@NonNull ChannelUID channelUID, @NonNull Command command) {
        /// Note: if communication with thing fails for some reason,
        // indicate that by setting the status with detail information:
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
        // "Could not control device at IP address x.x.x.x");

    }

    private synchronized void startDiscoveryService() {
        if (discoveryService != null) {
            return;
        }
        discoveryService = new BrickDaemonDiscoveryService(this);
        discoveryService.activate();
        registerFn.accept(discoveryService);
    }

    private synchronized void stopDiscoveryService() {
        if (discoveryService != null) {
            discoveryService.deactivate();
            deregisterFn.accept(discoveryService);
            discoveryService = null;
        }
    }

    private void connect() {
        ipcon.clearDisconnectedListeners();

        BrickDaemonConfig cfg = getConfigAs(BrickDaemonConfig.class);

        try {
            System.out.println("Connecting...");
            ipcon.connect(cfg.host, cfg.port);
            System.out.println("Connected");
        } catch (NetworkException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Could not connect: " + e.getLocalizedMessage());
            if (cfg.enableReconnect)
                connectFuture = scheduler.schedule(this::connect, cfg.reconnectInterval, TimeUnit.SECONDS);
            return;
        } catch (AlreadyConnectedException e) {

        }

        if (cfg.auth) {
            try {
                System.out.println("Authenticating...");
                ipcon.authenticate(cfg.password);
                System.out.println("Authenticated");
            } catch (TinkerforgeException e) {
                if (e instanceof TimeoutException) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                                "Could not authenticate (maybe the password was wrong?): " + e.getLocalizedMessage());
                }
                else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                                "Could not authenticate: " + e.getLocalizedMessage());
                }
                return;
            }
        }

        ipcon.addDisconnectedListener(reason -> {
            updateStatus(ThingStatus.OFFLINE);
            this.stopDiscoveryService();

            if (cfg.enableReconnect)
                connectFuture = scheduler.schedule(this::connect, cfg.reconnectInterval, TimeUnit.SECONDS);
        });

        this.startDiscoveryService();

        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void initialize() {
        // The framework requires you to return from this method quickly. Also, before leaving this method a thing
        // status from one of ONLINE, OFFLINE or UNKNOWN must be set. This might already be the real thing status in
        // case you can decide it directly.
        // In case you can not decide the thing status directly (e.g. for long running connection handshake using WAN
        // access or similar) you should set status UNKNOWN here and then decide the real status asynchronously in the
        // background.

        // set the thing status to UNKNOWN temporarily and let the background task decide for the real status.
        // the framework is then able to reuse the resources from the thing handler initialization.
        // we set this upfront to reliably check status updates in unit tests.
        updateStatus(ThingStatus.UNKNOWN);
        scheduler.execute(this::connect);
    }

    public void enumerate() throws NotConnectedException {
        try {
            ipcon.enumerate();
        }
        catch (NotConnectedException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Could not enumerate: Not connected.");
        }
    }

    public void addEnumerateListener(EnumerateListener listener) {
        ipcon.addEnumerateListener(listener);
    }

    public void removeEnumerateListener(EnumerateListener listener) {
        ipcon.removeEnumerateListener(listener);
    }

    @Override
    public void handleRemoval() {
        try {
            if (connectFuture != null)
                connectFuture.cancel(true);
            ipcon.clearDisconnectedListeners();
            this.stopDiscoveryService();
            ipcon.disconnect();
        } catch (NotConnectedException e) {
        }
        super.handleRemoval();
    }

    @Override
    public void dispose() {
        try {
            if (connectFuture != null)
                connectFuture.cancel(true);
            ipcon.clearDisconnectedListeners();
            this.stopDiscoveryService();
            ipcon.disconnect();
        } catch (NotConnectedException e) {
        }
    }
}
