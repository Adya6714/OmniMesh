package omnimesh.command1.data

import androidx.room.TypeConverter
import omnimesh.command1.command.TimelineEventType

class RoomConverters {
    @TypeConverter
    fun timelineEventTypeToString(value: TimelineEventType): String = value.name

    @TypeConverter
    fun stringToTimelineEventType(value: String): TimelineEventType =
        TimelineEventType.valueOf(value)
}
