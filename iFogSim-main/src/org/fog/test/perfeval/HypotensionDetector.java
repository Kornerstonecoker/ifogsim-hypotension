package org.fog.modules;

import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.entities.FogDevice;
import org.fog.entities.Tuple;
import org.fog.utils.TimeKeeper;
import org.fog.application.AppModule;

public class HypotensionDetector extends AppModule {

    public HypotensionDetector(String name, String appId, int userId, int mips, int ram, long bw, long size, String vmm, FogDevice device) {
        super(name, appId, userId, mips, ram, bw, size, vmm, device);
    }

    @Override
    public void processTupleArrival(SimEvent ev){
        Tuple tuple = (Tuple) ev.getData();
        TimeKeeper.getInstance().tupleStartedExecution(tuple.getActualTupleId());
        super.processTupleArrival(ev);
    }

    @Override
    public void processTupleCompletion(Tuple tuple){
        super.processTupleCompletion(tuple);
        TimeKeeper.getInstance().tupleFinished(tuple);
    }
}
