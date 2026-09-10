import pm4py
import knime.extension as knext
from utils import knime_util
from utils.petri_net_type import PetriNetPortObject, PetriNetSpec
from utils.petri_net_type import convert_port_object_to_pm4py
import pandas as pd
import logging
import os

LOGGER = logging.getLogger(__name__)

script_dir = os.path.dirname(os.path.abspath(__file__))
path_to_icon = os.path.abspath(os.path.join(script_dir, "..", "icon", "category-conformance.png"))

petri_net_port_type = knext.nodes.get_port_type_for_id(
    "org.pm4knime.portobject.PetriNetPortObject"
)

@knext.node(name="Simplicity Evaluator",
            node_type=knext.NodeType.OTHER,
            icon_path=path_to_icon,
            category="/community/processmining/conformance")
@knime_util.create_node_description(
    short_description="Evaluate the simplicity of a Petri net.",
    description="This node evaluates the simplicity of the input Petri net. The criterion used for simplicity is the inverse arc degree."
)
@knext.input_port(name="Petri Net", description="A Petri Net.", port_type=petri_net_port_type)
@knext.output_table(name="Metrics Table",
                    description="A metrics table with a simplicity score. The computed score is a number between 0 and 1, where 0 stands for the lowest simplicity and 1 stands for the highest simplicity.")
class SimplicityChecker:

    def configure(self, configure_context: knext.ConfigurationContext, petri_net_spec: PetriNetSpec):
        return None

    def execute(self, exec_context, petri_net: PetriNetPortObject):
        net, initial_marking, final_marking = convert_port_object_to_pm4py(petri_net)
        
        simplicity = pm4py.algo.evaluation.simplicity.algorithm.apply(petri_net=net)
        res = pd.DataFrame.from_dict({"simplicity": simplicity}, orient='index', columns=['Value'])
        return knext.Table.from_pandas(res)
