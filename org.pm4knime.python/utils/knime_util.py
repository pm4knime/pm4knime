import logging
import knime.extension as knext

LOGGER = logging.getLogger(__name__)


ZONED_DATE_TIME_ZONE_VALUE = "org.knime.core.data.v2.time.ZonedDateTimeValueFactory2"
LOCAL_TIME_VALUE = "org.knime.core.data.v2.time.LocalTimeValueFactory"
LOCAL_DATE_VALUE = "org.knime.core.data.v2.time.LocalDateValueFactory"
LOCAL_DATE_TIME_VALUE = "org.knime.core.data.v2.time.LocalDateTimeValueFactory"


def create_node_description(short_description: str, description: str, references: dict = None):
    """Generates a standardized node description."""

    def set_description(node_factory):
        s = f"{short_description}\n"
        s += f"{description}\n\n"
        # s += "___\n\n"  # separator line between description and general part
        if references:
            s += "External resources:"
            s += "\n\n"
            for key in references:
                s += f"- [{key}]({references[key]})\n"
        node_factory.__doc__ = s
        return node_factory

    return set_description


def is_numeric(column: knext.Column) -> bool:
    """
    Checks if column is numeric e.g. int, long or double.
    @return: True if Column is numeric
    """
    return (
            column.ktype == knext.double()
            or column.ktype == knext.int32()
            or column.ktype == knext.int64()
    )


def is_string(column: knext.Column) -> bool:
    """
    Checks if column is string
    @return: True if Column is string
    """
    return column.ktype == knext.string()


def is_boolean(column: knext.Column) -> bool:
    """
    Checks if column is boolean
    @return: True if Column is boolean
    """
    return column.ktype == knext.bool_()


def is_binary(column: knext.Column) -> bool:
    """
    Checks if column is binary
    @return: True if Column is binary
    """
    return column.ktype == knext.blob


def is_date(column: knext.Column) -> bool:
    return column.ktype == knext.datetime()


def is_zoned_datetime(column: knext.Column) -> bool:
    """
    Checks if date&time column contains has the timezone or not.
    @return: True if selected date&time column has time zone
    """
    return __is_type_x(column, ZONED_DATE_TIME_ZONE_VALUE)


def is_datetime(column: knext.Column) -> bool:
    """
    Checks if a column is of type Date&Time.
    @return: True if selected column is of type date&time
    """
    return __is_type_x(column, LOCAL_DATE_TIME_VALUE)


def is_time(column: knext.Column) -> bool:
    """
    Checks if a column is of type Time only.
    @return: True if selected column has only time.
    """
    return __is_type_x(column, LOCAL_TIME_VALUE)


def boolean_or(*functions):
    """
    Return True if any of the given functions returns True
    @return: True if any of the functions returns True
    """

    def new_function(*args, **kwargs):
        return any(f(*args, **kwargs) for f in functions)

    return new_function


def is_type_timestamp(column: knext.Column):
    """
    This function checks on all the supported timestamp columns supported in KNIME.
    Note that legacy date&time types are not supported.
    @return: True if date&time column is compatible with the respective logical types supported in KNIME.
    """
    return boolean_or(is_time, is_date, is_datetime, is_zoned_datetime)(column)


def is_petri_net(column: knext.Column) -> bool:
    return __is_type_x(column, "org.pm4knime.node.conversion.pn2table.PetriNetCell")


def __is_type_x(column: knext.Column, t: str) -> bool:
    """
    Checks if column contains the given type whereas type can be :
    DateTime, Date, Time, ZonedDateTime
    @return: True if column type is of type timestamp
    """
    return (
            isinstance(column.ktype, knext.LogicalType)
            and t in column.ktype.logical_type
    )
