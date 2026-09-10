package org.pm4knime.util;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;
import org.pm4knime.portobject.factories.PetriNetPortViewFactories;
import org.pm4knime.portobject.factories.BPMNPortViewFactories;
import org.pm4knime.portobject.factories.CausalGraphPortViewFactories;
import org.pm4knime.portobject.factories.DFGPortViewFactories;
import org.pm4knime.portobject.factories.HybridPetriNetPortViewFactories;
import org.pm4knime.portobject.factories.ProcessTreePortViewFactories;
import org.pm4knime.portobject.factories.AlignmentPortViewFactories;

/**
 * This is the eclipse bundle activator.
 * Note: KNIME node developers probably won't have to do anything in here, 
 * as this class is only needed by the eclipse platform/plugin mechanism.
 * If you want to move/rename this file, make sure to change the plugin.xml
 * file in the project root directory accordingly.
 *
 * @author 
 */
public class PM4KNIMEPlugin extends Plugin {
    // The shared instance.
    private static PM4KNIMEPlugin plugin;

    /**
     * The constructor.
     */
    public PM4KNIMEPlugin() {
        super();
        plugin = this;
    }

    /**
     * This method is called upon plug-in activation.
     * 
     * @param context The OSGI bundle context
     * @throws Exception If this plugin could not be started
     */
    @Override
    public void start(final BundleContext context) throws Exception {
        super.start(context);
        PetriNetPortViewFactories.register();
        BPMNPortViewFactories.register();
        CausalGraphPortViewFactories.register();
        DFGPortViewFactories.register();
        HybridPetriNetPortViewFactories.register();
        ProcessTreePortViewFactories.register();
        AlignmentPortViewFactories.register();
    }

    /**
     * This method is called when the plug-in is stopped.
     * 
     * @param context The OSGI bundle context
     * @throws Exception If this plugin could not be stopped
     */
    @Override
    public void stop(final BundleContext context) throws Exception {
        super.stop(context);
        plugin = null;
    }

    /**
     * Returns the shared instance.
     * 
     * @return Singleton instance of the Plugin
     */
    public static PM4KNIMEPlugin getDefault() {
        return plugin;
    }

}

