package org.fog.modules;

import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppModule;
import org.fog.entities.Tuple;
import org.fog.utils.TimeKeeper;

public class ClientModule extends AppModule {
    public ClientModule(String name, String appId, int userId, int mips,
                        int ram, long bw, long size, String vmm, FogDevice device) {
        super(name, appId, userId, mips, ram, bw, size, vmm, device);
    }

    @Override
    public void processTupleArrival(SimEvent ev) {
        Tuple tuple = (Tuple)ev.getData();
        // mark the start of processing for the loop-monitor
        TimeKeeper.getInstance().tupleStartedExecution(tuple.getActualTupleId());
        super.processTupleArrival(ev);
    }

    @Override
    public void processTupleCompletion(Tuple tuple) {
        super.processTupleCompletion(tuple);
        // mark the end of processing
        TimeKeeper.getInstance().tupleFinished(tuple);
    }
}
