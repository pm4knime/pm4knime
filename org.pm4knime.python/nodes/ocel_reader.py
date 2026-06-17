import os
import logging

import knime.extension as knext
import pandas as pd

from utils import knime_util
from utils import ocel_util


LOGGER = logging.getLogger(__name__)

script_dir = os.path.dirname(os.path.abspath(__file__))
path_to_icon = os.path.abspath(os.path.join(script_dir, "..", "icon", "read.png"))


def _schema_from_dataframe(df: pd.DataFrame) -> knext.Schema:
    columns = []
    for name, dtype in df.dtypes.items():
        if pd.api.types.is_bool_dtype(dtype):
            ktype = knext.bool_()
        elif pd.api.types.is_integer_dtype(dtype):
            ktype = knext.int64()
        elif pd.api.types.is_float_dtype(dtype):
            ktype = knext.double()
        elif pd.api.types.is_datetime64_any_dtype(dtype):
            ktype = knext.datetime(date=True, time=True, timezone=True)
        else:
            ktype = knext.string()
        columns.append(knext.Column(ktype, str(name)))
    return knext.Schema.from_columns(columns)


@knext.node(
    name="OCEL Reader",
    node_type=knext.NodeType.SOURCE,
    icon_path=path_to_icon,
    category="/community/processmining/io/ioRead",
)
@knime_util.create_node_description(
    short_description="Read an object-centric event log (OCEL) into KNIME tables.",
    description=(
        "Reads OCEL 2.0 JSON, OCEL 2.0 SQLite, simple OCEL XML, and legacy JSON-OCEL files. "
        "The first output is a combined event-object table that contains event data, object data, "
        "event-object relations, and latest object attribute values at the event timestamp. "
        "The remaining outputs expose the normalized OCEL components for further processing."
    ),
)
@knext.output_table(name="Combined Event-Object Table", description="One row per event-object relation with event and object attributes combined.")
@knext.output_table(name="Events", description="One row per OCEL event, including event attributes.")
@knext.output_table(name="Objects", description="One row per OCEL object, including latest object attributes.")
@knext.output_table(name="Event-Object Relations", description="Normalized event-to-object relations including qualifiers.")
@knext.output_table(name="Object Attribute Changes", description="Long table with dynamic object attribute values over time.")
@knext.output_table(name="Object-Object Relations", description="Normalized object-to-object relations including qualifiers.")
class OCELReader(knext.PythonNode):
    ocel_path = knext.LocalPathParameter(
        label="OCEL file",
        description="Path to an OCEL file. Supported suffixes include .jsonocel, .json, .sqlite, .sqlite3, .xmlocel, and .xml.",
    )

    use_pm4py = knext.BoolParameter(
        label="Prefer PM4Py importer when available",
        description="Use PM4Py's OCEL importer first and fall back to the built-in parser if PM4Py cannot read the file.",
        default_value=True,
        is_advanced=True,
    )

    def configure(self, configure_context: knext.ConfigurationContext):
        if self.ocel_path is None or str(self.ocel_path).strip() == "":
            raise knext.InvalidParametersError("Please configure an OCEL file path.")
        try:
            tables = ocel_util.read_ocel_tables(str(self.ocel_path), prefer_pm4py=bool(self.use_pm4py))
        except Exception as exc:
            raise knext.InvalidParametersError(str(exc)) from exc
        return (
            _schema_from_dataframe(tables.combined),
            _schema_from_dataframe(tables.events),
            _schema_from_dataframe(tables.objects),
            _schema_from_dataframe(tables.event_object_relations),
            _schema_from_dataframe(tables.object_attribute_changes),
            _schema_from_dataframe(tables.object_object_relations),
        )

    def execute(self, exec_context):
        tables = ocel_util.read_ocel_tables(str(self.ocel_path), prefer_pm4py=bool(self.use_pm4py))
        LOGGER.info(
            "Read OCEL with %d events, %d objects, and %d event-object relations.",
            len(tables.events),
            len(tables.objects),
            len(tables.event_object_relations),
        )
        return (
            knext.Table.from_pandas(tables.combined, row_ids="generate"),
            knext.Table.from_pandas(tables.events, row_ids="generate"),
            knext.Table.from_pandas(tables.objects, row_ids="generate"),
            knext.Table.from_pandas(tables.event_object_relations, row_ids="generate"),
            knext.Table.from_pandas(tables.object_attribute_changes, row_ids="generate"),
            knext.Table.from_pandas(tables.object_object_relations, row_ids="generate"),
        )
