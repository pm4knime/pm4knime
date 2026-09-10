import pm4py
import knime.extension as knext
from utils import knime_util
from utils.petri_net_type import PetriNetPortObject, PetriNetSpec
from utils.petri_net_type import convert_port_object_to_pm4py
import pandas as pd
import pytz
import logging
import os


LOGGER = logging.getLogger(__name__)

script_dir = os.path.dirname(os.path.abspath(__file__))
path_to_icon = os.path.abspath(os.path.join(script_dir, "..", "icon", "category-conformance.png"))

petri_net_port_type = knext.nodes.get_port_type_for_id(
    "org.pm4knime.portobject.PetriNetPortObject"
)

@knext.node(name="Token-Based Precision Checker",
            node_type=knext.NodeType.OTHER,
            icon_path=path_to_icon,
            category="/community/processmining/conformance")
@knime_util.create_node_description(
    short_description="Evaluate the precision of a Petri net with respect to an event log.",
    description="This node evaluates the precision of the input Petri net with respect to the input event log. The precision is computed using the token-based replay method."
)
@knext.input_table(name="Event Table", description="An Event Table.")
@knext.input_port(name="Petri Net", description="A Petri Net.", port_type=petri_net_port_type)
@knext.output_table(name="Metrics Table",
                    description="A metrics table with a precision score. The computed score is a number between 0 and 1, where 0 stands for the lowest precision and 1 stands for the highest precision.")
class PrecisionChecker:
    column_param_case = knext.ColumnParameter(label="Case Column",
                                              description="The column that contains the case identifiers.",
                                              port_index=0)
    column_param_activity = knext.ColumnParameter(label="Activity Column",
                                                  description="The column that contains the activities.",
                                                  port_index=0)
    column_param_time = knext.ColumnParameter(label="Time Column",
                                              description="The column that contains the timestamps."
                                                          "This column must have the type 'Local Date Time'.",
                                              port_index=0,
                                              column_filter=knime_util.is_type_timestamp)

    def configure(self, configure_context: knext.ConfigurationContext, input_schema_1: knext.Schema,
                  petri_net_spec: PetriNetSpec):
        for par in [self.column_param_case, self.column_param_time, self.column_param_activity]:
            if par is None or par == "":
                raise knext.InvalidParametersError("Parameters not set! Please configure the node!")
        return None

    def execute(self, exec_context, input_1, petri_net: PetriNetPortObject):
        event_log = input_1.to_pandas()
        
        net, initial_marking, final_marking = convert_port_object_to_pm4py(petri_net)

        event_log[self.column_param_time + "UTC"] = pd.to_datetime(event_log[self.column_param_time], utc=True)
        event_log = event_log.sort_values(by=[self.column_param_case, self.column_param_time + "UTC"])

        precision = pm4py.precision_token_based_replay(log=event_log,
                                                       petri_net=net,
                                                       initial_marking=initial_marking,
                                                       final_marking=final_marking,
                                                       activity_key=self.column_param_activity,
                                                       case_id_key=self.column_param_case,
                                                       timestamp_key=self.column_param_time + "UTC")
        res = pd.DataFrame.from_dict({"precision": precision}, orient='index', columns=['Value'])
        return knext.Table.from_pandas(res)
