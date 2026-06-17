"""Utility functions for reading object-centric event logs.

The functions in this module intentionally avoid KNIME-specific imports so they can be
unit-tested outside KNIME and reused by multiple Python nodes.
"""

from __future__ import annotations

from dataclasses import dataclass
import json
import os
import re
import sqlite3
import xml.etree.ElementTree as ET
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Tuple

import numpy as np
import pandas as pd


STANDARD_EVENT_COLUMNS = ["event_id", "activity", "timestamp"]
STANDARD_OBJECT_COLUMNS = ["object_id", "object_type"]
STANDARD_E2O_COLUMNS = ["event_id", "object_id", "qualifier"]
STANDARD_O2O_COLUMNS = ["object_id", "related_object_id", "qualifier"]
STANDARD_OBJECT_CHANGE_COLUMNS = ["object_id", "object_type", "attr_name", "attr_value", "timestamp"]



@dataclass
class OCELTables:
    """Tabular representation of an OCEL."""

    combined: pd.DataFrame
    events: pd.DataFrame
    objects: pd.DataFrame
    event_object_relations: pd.DataFrame
    object_attribute_changes: pd.DataFrame
    object_object_relations: pd.DataFrame


def read_ocel_tables(path: str, prefer_pm4py: bool = True) -> OCELTables:
    """Read an OCEL file and return KNIME-friendly tables.

    Supported directly: OCEL 2.0 JSON, legacy JSON-OCEL, OCEL 2.0 SQLite, and
    simple OCEL XML structures. If PM4Py is available in the runtime, it is used
    first because it already supports a broad range of OCEL variants.
    """

    if path is None or str(path).strip() == "":
        raise ValueError("No OCEL file path was configured.")

    path = os.path.abspath(os.path.expanduser(str(path)))
    if not os.path.exists(path):
        raise FileNotFoundError(f"OCEL file does not exist: {path}")
    if not os.path.isfile(path):
        raise ValueError(f"OCEL path is not a file: {path}")

    errors: List[str] = []

    if prefer_pm4py:
        try:
            return _read_with_pm4py(path)
        except Exception as exc:  # pragma: no cover - depends on optional PM4Py runtime
            errors.append(f"PM4Py importer failed: {type(exc).__name__}: {exc}")

    ext = os.path.splitext(path)[1].lower()
    try:
        if ext in {".json", ".jsonocel", ".ocel"}:
            return _read_json(path)
        if ext in {".sqlite", ".sqlite3", ".db"}:
            return _read_sqlite(path)
        if ext in {".xml", ".xmlocel"}:
            return _read_xml(path)

        # Last-resort content sniffing for files with unusual extensions.
        with open(path, "rb") as file:
            prefix = file.read(256).lstrip()
        if prefix.startswith(b"{"):
            return _read_json(path)
        if prefix.startswith(b"<"):
            return _read_xml(path)
        return _read_sqlite(path)
    except Exception as exc:
        errors.append(f"native importer failed: {type(exc).__name__}: {exc}")
        raise ValueError("Could not read OCEL file. " + " | ".join(errors)) from exc


def _read_with_pm4py(path: str) -> OCELTables:
    import pm4py  # type: ignore

    ext = os.path.splitext(path)[1].lower()
    candidate_names: List[str]
    if ext in {".sqlite", ".sqlite3", ".db"}:
        candidate_names = [
            "read_ocel2_sqlite",
            "read_ocel_sqlite",
            "read_ocel",
        ]
    elif ext in {".xml", ".xmlocel"}:
        candidate_names = ["read_ocel2_xml", "read_ocel_xml", "read_ocel"]
    else:
        candidate_names = ["read_ocel2_json", "read_ocel_json", "read_ocel"]

    last_error: Optional[Exception] = None
    ocel = None
    for name in candidate_names:
        fn = getattr(pm4py, name, None)
        if fn is None:
            continue
        try:
            ocel = fn(path)
            break
        except TypeError:
            try:
                ocel = fn(file_path=path)
                break
            except Exception as exc:  # pragma: no cover
                last_error = exc
        except Exception as exc:  # pragma: no cover
            last_error = exc

    if ocel is None:
        if last_error is not None:
            raise last_error
        raise ValueError("No PM4Py OCEL reader was found in this PM4Py installation.")

    events = _df_from_ocel_attr(ocel, "events", "event_id")
    objects = _df_from_ocel_attr(ocel, "objects", "object_id")
    relations = _df_from_ocel_attr(ocel, "relations", None)
    object_changes = _df_from_ocel_attr(ocel, "object_changes", None)
    o2o = _df_from_ocel_attr(ocel, "o2o", None)

    return _build_tables(events, objects, relations, object_changes, o2o)


def _df_from_ocel_attr(ocel: Any, attr: str, index_name: Optional[str]) -> pd.DataFrame:
    value = getattr(ocel, attr, None)
    if value is None:
        return pd.DataFrame()
    if not isinstance(value, pd.DataFrame):
        try:
            value = pd.DataFrame(value)
        except Exception:
            return pd.DataFrame()
    df = value.copy()
    if index_name and index_name not in _normalized_column_lookup(df):
        if not isinstance(df.index, pd.RangeIndex) or df.index.name is not None:
            df = df.reset_index()
            first_col = df.columns[0]
            if first_col not in df.columns[1:]:
                df = df.rename(columns={first_col: index_name})
    return df


def _read_json(path: str) -> OCELTables:
    with open(path, "r", encoding="utf-8") as file:
        data = json.load(file)

    if "events" in data and "objects" in data:
        return _read_ocel2_json_dict(data)
    if "ocel:events" in data and "ocel:objects" in data:
        return _read_legacy_json_dict(data)

    raise ValueError("JSON file is neither an OCEL 2.0 JSON nor a legacy JSON-OCEL file.")


def _read_ocel2_json_dict(data: Mapping[str, Any]) -> OCELTables:
    events_rows: List[Dict[str, Any]] = []
    e2o_rows: List[Dict[str, Any]] = []
    object_rows: List[Dict[str, Any]] = []
    object_change_rows: List[Dict[str, Any]] = []
    o2o_rows: List[Dict[str, Any]] = []

    for event in data.get("events", []) or []:
        event_id = _first_not_none(event, ["id", "eventId", "event_id", "ocel:eid", "ocel_id"])
        if event_id is None:
            continue
        event_id = str(event_id)
        row: Dict[str, Any] = {
            "event_id": event_id,
            "activity": _first_not_none(event, ["type", "activity", "event_type", "ocel:activity", "ocel_type"]),
            "timestamp": _first_not_none(event, ["time", "timestamp", "ocel:timestamp", "ocel_time"]),
        }
        row.update(_wide_attributes(event.get("attributes")))
        events_rows.append(row)

        for rel in event.get("relationships", []) or []:
            object_id = _first_not_none(rel, ["objectId", "object-id", "object_id", "ocel:oid", "ocel_object_id"])
            if object_id is not None:
                e2o_rows.append(
                    {
                        "event_id": event_id,
                        "object_id": str(object_id),
                        "qualifier": _first_not_none(rel, ["qualifier", "ocel:qualifier", "role"]),
                    }
                )

    for obj in data.get("objects", []) or []:
        object_id = _first_not_none(obj, ["id", "objectId", "object_id", "ocel:oid", "ocel_id"])
        if object_id is None:
            continue
        object_id = str(object_id)
        object_type = _first_not_none(obj, ["type", "object_type", "ocel:type", "ocel_type"])
        row: Dict[str, Any] = {"object_id": object_id, "object_type": object_type}

        wide, long_rows = _object_attribute_rows(object_id, object_type, obj.get("attributes"))
        row.update(wide)
        object_change_rows.extend(long_rows)
        object_rows.append(row)

        for rel in obj.get("relationships", []) or []:
            related_id = _first_not_none(
                rel,
                ["objectId", "object-id", "object_id", "targetObjectId", "target_object_id", "ocel:oid", "ocel_target_id"],
            )
            if related_id is not None:
                o2o_rows.append(
                    {
                        "object_id": object_id,
                        "related_object_id": str(related_id),
                        "qualifier": _first_not_none(rel, ["qualifier", "ocel:qualifier", "role"]),
                    }
                )

    return _build_tables(
        pd.DataFrame(events_rows),
        pd.DataFrame(object_rows),
        pd.DataFrame(e2o_rows),
        pd.DataFrame(object_change_rows),
        pd.DataFrame(o2o_rows),
    )


def _read_legacy_json_dict(data: Mapping[str, Any]) -> OCELTables:
    events_rows: List[Dict[str, Any]] = []
    e2o_rows: List[Dict[str, Any]] = []
    object_rows: List[Dict[str, Any]] = []
    object_change_rows: List[Dict[str, Any]] = []

    for event_id, event in (data.get("ocel:events") or {}).items():
        row: Dict[str, Any] = {
            "event_id": str(event_id),
            "activity": event.get("ocel:activity"),
            "timestamp": event.get("ocel:timestamp"),
        }
        vmap = event.get("ocel:vmap") or {}
        if isinstance(vmap, Mapping):
            row.update(vmap)
        events_rows.append(row)

        for object_id in event.get("ocel:omap", []) or []:
            e2o_rows.append({"event_id": str(event_id), "object_id": str(object_id), "qualifier": None})

    for object_id, obj in (data.get("ocel:objects") or {}).items():
        object_type = obj.get("ocel:type")
        row: Dict[str, Any] = {"object_id": str(object_id), "object_type": object_type}
        ovmap = obj.get("ocel:ovmap") or {}
        if isinstance(ovmap, Mapping):
            for attr_name, value in ovmap.items():
                row[str(attr_name)] = value
                object_change_rows.append(
                    {
                        "object_id": str(object_id),
                        "object_type": object_type,
                        "attr_name": str(attr_name),
                        "attr_value": _stringify_if_complex(value),
                        "timestamp": pd.NaT,
                    }
                )
        object_rows.append(row)

    return _build_tables(
        pd.DataFrame(events_rows),
        pd.DataFrame(object_rows),
        pd.DataFrame(e2o_rows),
        pd.DataFrame(object_change_rows),
        pd.DataFrame(columns=STANDARD_O2O_COLUMNS),
    )


def _read_xml(path: str) -> OCELTables:
    tree = ET.parse(path)
    root = tree.getroot()

    events_rows: List[Dict[str, Any]] = []
    e2o_rows: List[Dict[str, Any]] = []
    object_rows: List[Dict[str, Any]] = []
    object_change_rows: List[Dict[str, Any]] = []
    o2o_rows: List[Dict[str, Any]] = []

    for event in _descendants_by_local_name(root, "event"):
        event_id = _attr_first(event, ["id", "eventId", "ocel_id"])
        if event_id is None:
            continue
        activity = _attr_first(event, ["type", "activity", "ocel_type"])
        timestamp = _attr_first(event, ["time", "timestamp", "ocel_time"])
        row: Dict[str, Any] = {"event_id": str(event_id), "activity": activity, "timestamp": timestamp}

        # OCEL 2.0 XML-style nested attributes/relationships.
        for child in list(event):
            lname = _local_name(child.tag)
            if lname == "time" and timestamp is None:
                row["timestamp"] = child.text
            elif lname in {"attribute", "string", "date", "float", "int", "boolean"}:
                key = _attr_first(child, ["name", "key"])
                if key and key not in {"id", "type", "time"}:
                    row[str(key)] = _attr_first(child, ["value"]) if _attr_first(child, ["value"]) is not None else child.text
            elif lname == "attributes":
                row.update(_xml_attributes_to_wide(child))
            elif lname == "relationships":
                for rel in list(child):
                    object_id = _attr_first(rel, ["objectId", "object-id", "object_id", "ocel_object_id", "id"])
                    if object_id is not None:
                        e2o_rows.append(
                            {
                                "event_id": str(event_id),
                                "object_id": str(object_id),
                                "qualifier": _attr_first(rel, ["qualifier", "role", "ocel_qualifier"]),
                            }
                        )

        # Legacy XML sometimes stores object references directly below event.
        for obj_ref in _descendants_by_local_name(event, "object"):
            if obj_ref is event:
                continue
            object_id = _attr_first(obj_ref, ["id", "objectId", "object-id", "object_id", "ocel_object_id"])
            if object_id is not None:
                e2o_rows.append(
                    {
                        "event_id": str(event_id),
                        "object_id": str(object_id),
                        "qualifier": _attr_first(obj_ref, ["qualifier", "role", "ocel_qualifier"]),
                    }
                )
        events_rows.append(row)

    for obj in _descendants_by_local_name(root, "object"):
        # Skip object references that are nested in an event relationship section.
        parentish_id = _attr_first(obj, ["type", "object_type", "ocel_type"])
        if parentish_id is None and not list(obj):
            continue
        object_id = _attr_first(obj, ["id", "objectId", "object_id", "ocel_id"])
        if object_id is None:
            continue
        object_id = str(object_id)
        object_type = _attr_first(obj, ["type", "object_type", "ocel_type"])
        row: Dict[str, Any] = {"object_id": object_id, "object_type": object_type}
        for child in list(obj):
            lname = _local_name(child.tag)
            if lname == "attributes":
                wide, long_rows = _xml_object_attributes(object_id, object_type, child)
                row.update(wide)
                object_change_rows.extend(long_rows)
            elif lname == "relationships":
                for rel in list(child):
                    target_id = _attr_first(rel, ["objectId", "object-id", "targetObjectId", "target_object_id", "ocel_target_id", "id"])
                    if target_id is not None:
                        o2o_rows.append(
                            {
                                "object_id": object_id,
                                "related_object_id": str(target_id),
                                "qualifier": _attr_first(rel, ["qualifier", "role", "ocel_qualifier"]),
                            }
                        )
        object_rows.append(row)

    return _build_tables(
        pd.DataFrame(events_rows),
        pd.DataFrame(object_rows).drop_duplicates(subset=["object_id"], keep="first") if object_rows else pd.DataFrame(),
        pd.DataFrame(e2o_rows).drop_duplicates() if e2o_rows else pd.DataFrame(),
        pd.DataFrame(object_change_rows),
        pd.DataFrame(o2o_rows).drop_duplicates() if o2o_rows else pd.DataFrame(),
    )


def _read_sqlite(path: str) -> OCELTables:
    with sqlite3.connect(path) as conn:
        table_names = pd.read_sql_query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            conn,
        )["name"].tolist()
        tables = {name: pd.read_sql_query(f'SELECT * FROM "{name}"', conn) for name in table_names}

    event_map = _read_map_table(tables.get("event_map_type"), "ocel_type", "ocel_type_map")
    object_map = _read_map_table(tables.get("object_map_type"), "ocel_type", "ocel_type_map")

    events_base = tables.get("event", pd.DataFrame()).copy()
    objects_base = tables.get("object", pd.DataFrame()).copy()
    e2o = tables.get("event_object", pd.DataFrame()).copy()
    o2o = tables.get("object_object", pd.DataFrame()).copy()

    event_attr_tables = _collect_type_tables(tables, "event_", {"event_object", "event_map_type"})
    object_attr_tables = _collect_type_tables(tables, "object_", {"object_object", "object_map_type"})

    # Event type tables contain the timestamp and event attributes. Use map tables to
    # keep the original event type names when table names are sanitized.
    typed_event_frames: List[pd.DataFrame] = []
    for table_name, df in event_attr_tables.items():
        frame = df.copy()
        if frame.empty:
            continue
        mapped_name = _event_or_object_type_from_map(table_name, "event_", event_map)
        frame["__event_type_from_table"] = mapped_name
        typed_event_frames.append(frame)
    typed_events = pd.concat(typed_event_frames, ignore_index=True, sort=False) if typed_event_frames else pd.DataFrame()

    if not events_base.empty and not typed_events.empty:
        id_col = _find_col(typed_events, ["ocel_id", "event_id", "id"])
        if id_col:
            events = events_base.merge(typed_events, left_on="ocel_id", right_on=id_col, how="left", suffixes=("", "_typed"))
            if "ocel_type" in events.columns and "__event_type_from_table" in events.columns:
                events["ocel_type"] = events["ocel_type"].combine_first(events["__event_type_from_table"])
        else:
            events = events_base
    else:
        events = events_base if not events_base.empty else typed_events

    # Object type tables contain change records for dynamic attributes.
    object_change_rows: List[pd.DataFrame] = []
    object_latest_frames: List[pd.DataFrame] = []
    for table_name, df in object_attr_tables.items():
        if df.empty:
            continue
        mapped_type = _event_or_object_type_from_map(table_name, "object_", object_map)
        frame = df.copy()
        frame["__object_type_from_table"] = mapped_type
        id_col = _find_col(frame, ["ocel_id", "object_id", "id"])
        time_col = _find_col(frame, ["ocel_time", "timestamp", "time"])
        changed_col = _find_col(frame, ["ocel_changed_field", "changed_field"])
        attr_cols = [
            c
            for c in frame.columns
            if c not in {id_col, time_col, changed_col, "__object_type_from_table"}
            and not c.endswith("_typed")
        ]
        if id_col:
            latest = frame.sort_values(by=[id_col, time_col] if time_col else [id_col]).drop_duplicates(id_col, keep="last")
            latest = latest[[id_col] + attr_cols].rename(columns={id_col: "ocel_id"})
            object_latest_frames.append(latest)
        for attr in attr_cols:
            change = pd.DataFrame(
                {
                    "object_id": frame[id_col].astype(str) if id_col else None,
                    "object_type": mapped_type,
                    "attr_name": attr,
                    "attr_value": frame[attr],
                    "timestamp": frame[time_col] if time_col else pd.NaT,
                }
            )
            # Keep rows that actually have a value. If ocel_changed_field is present,
            # this still keeps initial snapshots where changed_field is NULL.
            change = change[change["attr_value"].notna()]
            object_change_rows.append(change)

    objects = objects_base.copy()
    for latest in object_latest_frames:
        objects = objects.merge(latest, on="ocel_id", how="left", suffixes=("", "_latest")) if not objects.empty else latest

    object_changes = pd.concat(object_change_rows, ignore_index=True, sort=False) if object_change_rows else pd.DataFrame()

    return _build_tables(events, objects, e2o, object_changes, o2o)


def _read_map_table(df: Optional[pd.DataFrame], key_col: str, value_col: str) -> Dict[str, str]:
    if df is None or df.empty or key_col not in df.columns or value_col not in df.columns:
        return {}
    return {str(row[value_col]): str(row[key_col]) for _, row in df.iterrows()}


def _collect_type_tables(tables: Mapping[str, pd.DataFrame], prefix: str, excluded: set) -> Dict[str, pd.DataFrame]:
    return {
        name: df
        for name, df in tables.items()
        if name.startswith(prefix) and name not in excluded and not name.endswith("_map_type")
    }


def _event_or_object_type_from_map(table_name: str, prefix: str, type_map: Mapping[str, str]) -> str:
    sanitized = table_name[len(prefix) :]
    return type_map.get(sanitized, sanitized)


def _build_tables(
    events: pd.DataFrame,
    objects: pd.DataFrame,
    event_object_relations: pd.DataFrame,
    object_attribute_changes: pd.DataFrame,
    object_object_relations: pd.DataFrame,
) -> OCELTables:
    events = _standardize_events(events)
    objects = _standardize_objects(objects)
    event_object_relations = _standardize_event_object_relations(event_object_relations)
    object_attribute_changes = _standardize_object_attribute_changes(object_attribute_changes)
    object_object_relations = _standardize_object_object_relations(object_object_relations)

    objects = _add_latest_object_attributes(objects, object_attribute_changes)
    combined = _build_combined_table(events, objects, event_object_relations, object_attribute_changes)

    return OCELTables(
        combined=combined,
        events=events,
        objects=objects,
        event_object_relations=event_object_relations,
        object_attribute_changes=object_attribute_changes,
        object_object_relations=object_object_relations,
    )


def _standardize_events(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    if df.empty and len(df.columns) == 0:
        return pd.DataFrame(columns=STANDARD_EVENT_COLUMNS)
    lookup = _normalized_column_lookup(df)
    id_col = _lookup_any(lookup, ["event_id", "ocel:eid", "ocel_eid", "ocel_id", "id", "eventid"])
    activity_col = _lookup_any(lookup, ["activity", "event_activity", "ocel:activity", "ocel_activity", "ocel_type", "type", "event_type", "__event_type_from_table"])
    time_col = _lookup_any(lookup, ["timestamp", "time:timestamp", "time_timestamp", "ocel:timestamp", "ocel_timestamp", "ocel_time", "time", "event_time"])
    df = _rename_if_present(df, id_col, "event_id")
    df = _rename_if_present(df, activity_col, "activity")
    df = _rename_if_present(df, time_col, "timestamp")
    for col in STANDARD_EVENT_COLUMNS:
        if col not in df.columns:
            df[col] = pd.NA
    df["event_id"] = df["event_id"].astype("string")
    df["activity"] = df["activity"].astype("string")
    df["timestamp"] = _to_datetime(df["timestamp"])
    df = _drop_internal_columns(_drop_duplicate_columns(df))
    return _reorder_columns(df, STANDARD_EVENT_COLUMNS)


def _standardize_objects(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    if df.empty and len(df.columns) == 0:
        return pd.DataFrame(columns=STANDARD_OBJECT_COLUMNS)
    lookup = _normalized_column_lookup(df)
    id_col = _lookup_any(lookup, ["object_id", "ocel:oid", "ocel_oid", "ocel_id", "id", "objectid"])
    type_col = _lookup_any(lookup, ["object_type", "ocel:type", "ocel_type", "type", "__object_type_from_table"])
    df = _rename_if_present(df, id_col, "object_id")
    df = _rename_if_present(df, type_col, "object_type")
    for col in STANDARD_OBJECT_COLUMNS:
        if col not in df.columns:
            df[col] = pd.NA
    df["object_id"] = df["object_id"].astype("string")
    df["object_type"] = df["object_type"].astype("string")
    df = _drop_internal_columns(_drop_duplicate_columns(df))
    return _reorder_columns(df, STANDARD_OBJECT_COLUMNS)


def _standardize_event_object_relations(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    if df.empty and len(df.columns) == 0:
        return pd.DataFrame(columns=STANDARD_E2O_COLUMNS)
    lookup = _normalized_column_lookup(df)
    event_col = _lookup_any(lookup, ["event_id", "ocel:eid", "ocel_eid", "ocel_event_id", "eid"])
    object_col = _lookup_any(lookup, ["object_id", "ocel:oid", "ocel_oid", "ocel_object_id", "oid"])
    qualifier_col = _lookup_any(lookup, ["qualifier", "ocel:qualifier", "ocel_qualifier", "role"])
    df = _rename_if_present(df, event_col, "event_id")
    df = _rename_if_present(df, object_col, "object_id")
    df = _rename_if_present(df, qualifier_col, "qualifier")
    for col in STANDARD_E2O_COLUMNS:
        if col not in df.columns:
            df[col] = pd.NA
    df["event_id"] = df["event_id"].astype("string")
    df["object_id"] = df["object_id"].astype("string")
    return _reorder_columns(_drop_duplicate_columns(df), STANDARD_E2O_COLUMNS)


def _standardize_object_attribute_changes(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    if df.empty and len(df.columns) == 0:
        return pd.DataFrame(columns=STANDARD_OBJECT_CHANGE_COLUMNS)
    lookup = _normalized_column_lookup(df)
    id_col = _lookup_any(lookup, ["object_id", "ocel:oid", "ocel_oid", "ocel_id", "id", "objectid"])
    type_col = _lookup_any(lookup, ["object_type", "ocel:type", "ocel_type", "type", "__object_type_from_table"])
    name_col = _lookup_any(lookup, ["attr_name", "attribute", "attribute_name", "name", "ocel_changed_field", "changed_field"])
    value_col = _lookup_any(lookup, ["attr_value", "attribute_value", "value"])
    time_col = _lookup_any(lookup, ["timestamp", "time", "ocel_time", "ocel:timestamp", "ocel_timestamp"])
    df = _rename_if_present(df, id_col, "object_id")
    df = _rename_if_present(df, type_col, "object_type")
    df = _rename_if_present(df, name_col, "attr_name")
    df = _rename_if_present(df, value_col, "attr_value")
    df = _rename_if_present(df, time_col, "timestamp")
    for col in STANDARD_OBJECT_CHANGE_COLUMNS:
        if col not in df.columns:
            df[col] = pd.NA
    df["object_id"] = df["object_id"].astype("string")
    df["object_type"] = df["object_type"].astype("string")
    df["attr_name"] = df["attr_name"].astype("string")
    df["timestamp"] = _to_datetime(df["timestamp"])
    return _reorder_columns(_drop_duplicate_columns(df), STANDARD_OBJECT_CHANGE_COLUMNS)


def _standardize_object_object_relations(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    if df.empty and len(df.columns) == 0:
        return pd.DataFrame(columns=STANDARD_O2O_COLUMNS)
    lookup = _normalized_column_lookup(df)
    source_col = _lookup_any(lookup, ["object_id", "source_object_id", "ocel_source_id", "source_id"])
    target_col = _lookup_any(lookup, ["related_object_id", "target_object_id", "ocel_target_id", "target_id"])
    qualifier_col = _lookup_any(lookup, ["qualifier", "ocel:qualifier", "ocel_qualifier", "role"])
    df = _rename_if_present(df, source_col, "object_id")
    df = _rename_if_present(df, target_col, "related_object_id")
    df = _rename_if_present(df, qualifier_col, "qualifier")
    for col in STANDARD_O2O_COLUMNS:
        if col not in df.columns:
            df[col] = pd.NA
    df["object_id"] = df["object_id"].astype("string")
    df["related_object_id"] = df["related_object_id"].astype("string")
    return _reorder_columns(_drop_duplicate_columns(df), STANDARD_O2O_COLUMNS)


def _build_combined_table(
    events: pd.DataFrame,
    objects: pd.DataFrame,
    event_object_relations: pd.DataFrame,
    object_attribute_changes: pd.DataFrame,
) -> pd.DataFrame:
    events_for_merge = _prefix_extra_columns(events, STANDARD_EVENT_COLUMNS, "event_")

    if event_object_relations.empty:
        base = events[["event_id"]].copy()
        base["object_id"] = pd.NA
        base["qualifier"] = pd.NA
    else:
        base = event_object_relations[STANDARD_E2O_COLUMNS].copy()

    base["__row_order"] = np.arange(len(base), dtype=np.int64)
    combined = base.merge(events_for_merge, on="event_id", how="left")

    as_of = pd.DataFrame(columns=["__row_order"])
    if not object_attribute_changes.empty and "timestamp" in combined.columns:
        as_of = _object_attributes_as_of(combined[["__row_order", "object_id", "timestamp"]], object_attribute_changes)

    # Avoid duplicate object_* columns: the as-of table is the most precise source
    # for object attributes at event time. Static object columns that are not present
    # in object_attribute_changes are still copied from the object table.
    as_of_attr_cols = set(as_of.columns) - {"__row_order"}
    object_cols = [c for c in objects.columns if c in STANDARD_OBJECT_COLUMNS]
    for col in objects.columns:
        if col in STANDARD_OBJECT_COLUMNS:
            continue
        prefixed = str(col) if str(col).startswith("object_") else f"object_{col}"
        if prefixed not in as_of_attr_cols:
            object_cols.append(col)
    objects_for_merge = _prefix_extra_columns(objects[object_cols].copy(), STANDARD_OBJECT_COLUMNS, "object_")

    combined = combined.merge(objects_for_merge, on="object_id", how="left")
    if not as_of.empty:
        combined = combined.merge(as_of, on="__row_order", how="left")

    combined = combined.sort_values(by=["timestamp", "event_id", "object_id"], kind="mergesort", na_position="last")
    combined = combined.drop(columns=["__row_order"], errors="ignore")
    leading = ["event_id", "activity", "timestamp", "object_id", "object_type", "qualifier"]
    return _reorder_columns(combined, leading).reset_index(drop=True)


def _prefix_extra_columns(df: pd.DataFrame, standard_cols: Sequence[str], prefix: str) -> pd.DataFrame:
    df = df.copy()
    mapping = {}
    for col in df.columns:
        if col not in standard_cols and not str(col).startswith(prefix):
            mapping[col] = f"{prefix}{col}"
    return df.rename(columns=mapping)


def _object_attributes_as_of(relations: pd.DataFrame, object_changes: pd.DataFrame) -> pd.DataFrame:
    changes = object_changes.copy()
    changes = changes[changes["object_id"].notna() & changes["attr_name"].notna()]
    if changes.empty:
        return pd.DataFrame(columns=["__row_order"])

    changes["timestamp"] = _to_datetime(changes["timestamp"])
    relations = relations.copy()
    relations["timestamp"] = _to_datetime(relations["timestamp"])

    attr_frames: List[pd.DataFrame] = []
    for attr_name, attr_changes in changes.groupby("attr_name", dropna=True):
        attr_col = f"object_{attr_name}"
        pieces: List[pd.DataFrame] = []
        for object_id, rel_group in relations.groupby("object_id", dropna=True):
            rel_group = rel_group.sort_values("timestamp", kind="mergesort")
            ch_group = attr_changes[attr_changes["object_id"].astype("string") == str(object_id)].copy()
            if ch_group.empty:
                continue
            ch_group = ch_group.sort_values("timestamp", kind="mergesort")
            if ch_group["timestamp"].isna().all():
                value = ch_group["attr_value"].dropna().iloc[-1] if ch_group["attr_value"].notna().any() else pd.NA
                part = rel_group[["__row_order"]].copy()
                part[attr_col] = value
                pieces.append(part)
                continue
            rel_valid = rel_group[rel_group["timestamp"].notna()].copy()
            rel_missing = rel_group[rel_group["timestamp"].isna()].copy()
            ch_valid = ch_group[ch_group["timestamp"].notna()].copy()
            if not rel_valid.empty and not ch_valid.empty:
                merged = pd.merge_asof(
                    rel_valid,
                    ch_valid[["timestamp", "attr_value"]],
                    on="timestamp",
                    direction="backward",
                    allow_exact_matches=True,
                )
                merged = merged[["__row_order", "attr_value"]].rename(columns={"attr_value": attr_col})
                pieces.append(merged)
            if not rel_missing.empty:
                latest = ch_group.sort_values("timestamp", kind="mergesort")["attr_value"].dropna()
                value = latest.iloc[-1] if len(latest) else pd.NA
                part = rel_missing[["__row_order"]].copy()
                part[attr_col] = value
                pieces.append(part)
        if pieces:
            attr_frames.append(pd.concat(pieces, ignore_index=True))

    if not attr_frames:
        return pd.DataFrame(columns=["__row_order"])

    result = attr_frames[0]
    for frame in attr_frames[1:]:
        result = result.merge(frame, on="__row_order", how="outer")
    return result


def _add_latest_object_attributes(objects: pd.DataFrame, object_changes: pd.DataFrame) -> pd.DataFrame:
    if object_changes.empty:
        return objects
    changes = object_changes[object_changes["object_id"].notna() & object_changes["attr_name"].notna()].copy()
    if changes.empty:
        return objects
    changes["timestamp"] = _to_datetime(changes["timestamp"])
    changes = changes.sort_values(by=["object_id", "attr_name", "timestamp"], kind="mergesort", na_position="first")
    latest = changes.drop_duplicates(subset=["object_id", "attr_name"], keep="last")
    if latest.empty:
        return objects
    wide = latest.pivot_table(index="object_id", columns="attr_name", values="attr_value", aggfunc="last").reset_index()
    wide.columns = [str(c) for c in wide.columns]
    if objects.empty:
        return _standardize_objects(wide)

    merged = objects.merge(wide, on="object_id", how="left", suffixes=("", "__latest"))
    for col in [c for c in wide.columns if c != "object_id"]:
        latest_col = f"{col}__latest"
        if latest_col in merged.columns:
            if col in merged.columns:
                merged[col] = merged[col].combine_first(merged[latest_col])
                merged = merged.drop(columns=[latest_col])
            else:
                merged = merged.rename(columns={latest_col: col})
    return merged


def _wide_attributes(attributes: Any) -> Dict[str, Any]:
    wide: Dict[str, Any] = {}
    if not attributes:
        return wide
    if isinstance(attributes, Mapping):
        return {str(k): _stringify_if_complex(v) for k, v in attributes.items()}
    for attr in attributes:
        if not isinstance(attr, Mapping):
            continue
        name = _first_not_none(attr, ["name", "key"])
        if name is None:
            continue
        if "value" in attr:
            value = attr.get("value")
        elif "values" in attr:
            values = attr.get("values") or []
            value = values[-1].get("value") if values and isinstance(values[-1], Mapping) else values[-1] if values else None
        else:
            value = None
        wide[str(name)] = _stringify_if_complex(value)
    return wide


def _object_attribute_rows(object_id: str, object_type: Any, attributes: Any) -> Tuple[Dict[str, Any], List[Dict[str, Any]]]:
    wide: Dict[str, Any] = {}
    rows: List[Dict[str, Any]] = []
    if not attributes:
        return wide, rows
    if isinstance(attributes, Mapping):
        iterable = [{"name": k, "value": v} for k, v in attributes.items()]
    else:
        iterable = attributes
    for attr in iterable:
        if not isinstance(attr, Mapping):
            continue
        name = _first_not_none(attr, ["name", "key"])
        if name is None:
            continue
        name = str(name)
        if "values" in attr and isinstance(attr.get("values"), list):
            for val in attr.get("values") or []:
                if isinstance(val, Mapping):
                    value = val.get("value")
                    timestamp = _first_not_none(val, ["time", "timestamp", "ocel_time"])
                else:
                    value = val
                    timestamp = None
                rows.append(
                    {
                        "object_id": object_id,
                        "object_type": object_type,
                        "attr_name": name,
                        "attr_value": _stringify_if_complex(value),
                        "timestamp": timestamp,
                    }
                )
                wide[name] = _stringify_if_complex(value)
        else:
            value = attr.get("value")
            timestamp = _first_not_none(attr, ["time", "timestamp", "ocel_time"])
            rows.append(
                {
                    "object_id": object_id,
                    "object_type": object_type,
                    "attr_name": name,
                    "attr_value": _stringify_if_complex(value),
                    "timestamp": timestamp,
                }
            )
            wide[name] = _stringify_if_complex(value)
    return wide, rows


def _xml_attributes_to_wide(parent: ET.Element) -> Dict[str, Any]:
    wide: Dict[str, Any] = {}
    for attr in list(parent):
        key = _attr_first(attr, ["name", "key"])
        if key:
            value = _attr_first(attr, ["value"])
            wide[str(key)] = value if value is not None else attr.text
    return wide


def _xml_object_attributes(object_id: str, object_type: Any, parent: ET.Element) -> Tuple[Dict[str, Any], List[Dict[str, Any]]]:
    wide: Dict[str, Any] = {}
    rows: List[Dict[str, Any]] = []
    for attr in list(parent):
        key = _attr_first(attr, ["name", "key"])
        if not key:
            continue
        value = _attr_first(attr, ["value"])
        if value is None:
            value = attr.text
        timestamp = _attr_first(attr, ["time", "timestamp", "ocel_time"])
        key = str(key)
        wide[key] = value
        rows.append({"object_id": object_id, "object_type": object_type, "attr_name": key, "attr_value": value, "timestamp": timestamp})
    return wide, rows


def _first_not_none(mapping: Mapping[str, Any], keys: Sequence[str]) -> Any:
    for key in keys:
        if key in mapping and mapping[key] is not None:
            return mapping[key]
    return None


def _attr_first(element: ET.Element, keys: Sequence[str]) -> Optional[str]:
    for key in keys:
        if key in element.attrib:
            return element.attrib[key]
    return None


def _local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1] if "}" in tag else tag


def _descendants_by_local_name(root: ET.Element, name: str) -> Iterable[ET.Element]:
    for element in root.iter():
        if _local_name(element.tag) == name:
            yield element


def _normalized_column_lookup(df: pd.DataFrame) -> Dict[str, str]:
    return {_normalize_name(c): c for c in df.columns}


def _normalize_name(name: Any) -> str:
    return str(name).strip().lower().replace(" ", "_").replace("-", "_")


def _lookup_any(lookup: Mapping[str, str], candidates: Sequence[str]) -> Optional[str]:
    for candidate in candidates:
        normalized = _normalize_name(candidate)
        if normalized in lookup:
            return lookup[normalized]
    return None


def _find_col(df: pd.DataFrame, candidates: Sequence[str]) -> Optional[str]:
    return _lookup_any(_normalized_column_lookup(df), candidates)


def _rename_if_present(df: pd.DataFrame, old: Optional[str], new: str) -> pd.DataFrame:
    if old is not None and old in df.columns and old != new:
        if new in df.columns:
            df[new] = df[new].combine_first(df[old])
            return df.drop(columns=[old])
        return df.rename(columns={old: new})
    return df


def _drop_duplicate_columns(df: pd.DataFrame) -> pd.DataFrame:
    return df.loc[:, ~pd.Index(df.columns).duplicated()].copy()


def _drop_internal_columns(df: pd.DataFrame) -> pd.DataFrame:
    internal = [c for c in df.columns if str(c).startswith("__")]
    return df.drop(columns=internal, errors="ignore") if internal else df


def _reorder_columns(df: pd.DataFrame, leading: Sequence[str]) -> pd.DataFrame:
    leading_existing = [c for c in leading if c in df.columns]
    rest = [c for c in df.columns if c not in leading_existing]
    return df[leading_existing + rest]


def _to_datetime(series: Any) -> pd.Series:
    if isinstance(series, pd.Series):
        result = pd.to_datetime(series, errors="coerce", utc=True)
    else:
        result = pd.to_datetime(pd.Series(series), errors="coerce", utc=True)
    return result


def _stringify_if_complex(value: Any) -> Any:
    if isinstance(value, (dict, list, tuple)):
        return json.dumps(value, ensure_ascii=False, sort_keys=True)
    return value


def _safe_identifier(value: Any) -> str:
    text = str(value)
    text = re.sub(r"[^A-Za-z0-9_]+", "_", text).strip("_")
    return text or "object"
