package net.rizkiepratama.hako.engine.tether

object PtpConstants {
    // Container Types
    const val TYPE_UNDEFINED = 0
    const val TYPE_COMMAND = 1
    const val TYPE_DATA = 2
    const val TYPE_RESPONSE = 3
    const val TYPE_EVENT = 4

    // Standard PTP Opcodes
    const val OC_GET_DEVICE_INFO = 0x1001
    const val OC_OPEN_SESSION = 0x1002
    const val OC_CLOSE_SESSION = 0x1003
    const val OC_GET_STORAGE_IDS = 0x1004
    const val OC_GET_STORAGE_INFO = 0x1005
    const val OC_GET_NUM_OBJECTS = 0x1006
    const val OC_GET_OBJECT_HANDLES = 0x1007
    const val OC_GET_OBJECT_INFO = 0x1008
    const val OC_GET_OBJECT = 0x1009
    const val OC_GET_THUMB = 0x100A

    // Sony Vendor Extension Opcodes
    const val OC_SONY_SDIO_CONNECT = 0x9201
    const val OC_SONY_SDIO_GET_EXT_DEVICE_INFO = 0x9202
    const val OC_SONY_GET_DEVICE_PROP_DESC = 0x9203
    const val OC_SONY_GET_ALL_EXT_DEVICE_PROP_INFO = 0x9209


    // PTP Response Codes
    const val RC_OK = 0x2001
    const val RC_GENERAL_ERROR = 0x2002
    const val RC_SESSION_NOT_OPEN = 0x2003
    const val RC_INVALID_TRANSACTION_ID = 0x2004
    const val RC_OPERATION_NOT_SUPPORTED = 0x2005
    const val RC_PARAMETER_NOT_SUPPORTED = 0x2006

    // PTP / Sony Event Codes
    const val EC_OBJECT_ADDED = 0x4002
    const val EC_OBJECT_REMOVED = 0x4003
    const val EC_STORE_FULL = 0x4004
    const val EC_STORAGE_INFO_CHANGED = 0x400C
    const val EC_SONY_OBJECT_ADDED1 = 0xC001
    const val EC_SONY_OBJECT_ADDED2 = 0xC201
    const val EC_SONY_OBJECT_INFO_CHANGED = 0xC202
    const val EC_SONY_PROP_CHANGED = 0xC203

    // Image Object Formats
    const val FMT_UNDEFINED = 0x3000
    const val FMT_EXIF_JPEG = 0x3801
    const val FMT_SONY_RAW = 0xB101
    const val FMT_ARW = 0x3005
    const val FMT_TIFF = 0x3802

    // Header Sizes
    const val HEADER_SIZE = 12
}
