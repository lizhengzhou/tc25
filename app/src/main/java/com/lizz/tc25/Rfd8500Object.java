package com.lizz.tc25;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.webkit.JavascriptInterface;

import com.zebra.rfid.api3.Antennas;
import com.zebra.rfid.api3.ENUM_TRANSPORT;
import com.zebra.rfid.api3.ENUM_TRIGGER_MODE;
import com.zebra.rfid.api3.Events;
import com.zebra.rfid.api3.HANDHELD_TRIGGER_EVENT_TYPE;
import com.zebra.rfid.api3.INVENTORY_STATE;
import com.zebra.rfid.api3.InvalidUsageException;
import com.zebra.rfid.api3.OperationFailureException;
import com.zebra.rfid.api3.RFIDReader;
import com.zebra.rfid.api3.ReaderDevice;
import com.zebra.rfid.api3.Readers;
import com.zebra.rfid.api3.RfidEventsListener;
import com.zebra.rfid.api3.RfidReadEvents;
import com.zebra.rfid.api3.RfidStatusEvents;
import com.zebra.rfid.api3.SESSION;
import com.zebra.rfid.api3.SL_FLAG;
import com.zebra.rfid.api3.START_TRIGGER_TYPE;
import com.zebra.rfid.api3.STATUS_EVENT_TYPE;
import com.zebra.rfid.api3.STOP_TRIGGER_TYPE;
import com.zebra.rfid.api3.TagData;
import com.zebra.rfid.api3.TriggerInfo;

import java.util.ArrayList;

public class Rfd8500Object implements RfidEventsListener {

    public static final String TAG = "rfd8500";

    private static final String template = "{\"error\":\"%s\",\"tagid\":\"%s\",\"data\":\"%s\"}";

    private Context context;
    private Handler mHandler;

    private static Readers readers = null;
    private static RFIDReader reader;
//    private static ReaderDevice readerDevice;

    String pairName = "";

    String lastError = "";
    String tagId = "";
    String data = "";


    public Rfd8500Object(Context context, Handler handler) {
        this.context = context;
        this.mHandler = handler;
    }

    /**
     * 根据SN连接RFD8500
     *
     * @param name
     * @return
     */
    @JavascriptInterface
    public String connect(String name) {

        try {
            Log.d(TAG, "connect: " + name);

            if (name == null || name.equals("")) {
                throw new RuntimeException("SN不能为空");
            }

            if (name.length() != 14) {
                throw new RuntimeException("SN格式不正确");
            }

            if (!("RFD8500" + name).equals(this.pairName)) {
                dispose();
            }

            pairName = "RFD8500" + name;

            readers = new Readers(context, ENUM_TRANSPORT.BLUETOOTH);

            ArrayList<ReaderDevice> readerDevices = readers.GetAvailableRFIDReaderList();

            if (readerDevices == null || readerDevices.size() == 0)
                throw new RuntimeException("请先配对RFD8500蓝牙读写器");

            for (ReaderDevice device : readerDevices) {
                if (device.getName().equals(pairName)) {
//                    readerDevice = device;
                    reader = device.getRFIDReader();
                }
            }

            if (reader == null) throw new RuntimeException("请先配对RFD8500蓝牙读写器:" + pairName);

            try {
                reader.connect();
            } catch (OperationFailureException e) {
                e.printStackTrace();
                throw new RuntimeException("请检查读写器电源是否打开" + e.getResults().toString());
            }

            if (!reader.isConnected()) throw new RuntimeException("连接失败");

            try {
                ConfigureReader();
            } catch (OperationFailureException e) {
                e.printStackTrace();
                throw new RuntimeException("读写器配置失败:" + e.getVendorMessage() + e.getResults().toString());
            }

            return String.format(template, "", "", "");
        } catch (Exception e) {
            e.printStackTrace();
            this.lastError = e.getMessage();
            return String.format(template, lastError, "", "");
        }
    }

    /**
     * 读卡
     *
     * @return
     */
    @JavascriptInterface
    public String read() {
        return String.format(template, this.lastError, this.tagId, this.data);
    }

    /**
     * 写卡
     *
     * @param data
     * @return
     */
    @JavascriptInterface
    public boolean write(String data) {
        Log.d(TAG, "write: " + data);


        return false;
    }

    /**
     * 状态查询
     *
     * @return
     */
    @JavascriptInterface
    public String status() {
        return String.format(template, this.lastError, "", "");
    }

    /**
     * 断开连接
     *
     * @return
     */
    public boolean disconnect() {
        Log.d(TAG, "disconnect ");
        try {
            if (reader != null) {
                reader.Events.removeEventsListener(this);
                reader.disconnect();
            }
            return true;
        } catch (InvalidUsageException e) {
            e.printStackTrace();
        } catch (OperationFailureException e) {
            e.printStackTrace();
            this.lastError = "读失败" + e.getVendorMessage() + e.getResults().toString();
        } catch (Exception e) {
            e.printStackTrace();
            this.lastError = "读失败" + e.getMessage();
        }
        return false;
    }

    /**
     * 重新连接
     *
     * @return
     */
    public boolean reconnect() {
        if (reader != null) {
            Log.d(TAG, "connect " + reader.getHostName());
            try {
                if (!reader.isConnected()) {
                    reader.connect();
                    ConfigureReader();
                    return true;
                }
            } catch (InvalidUsageException e) {
                e.printStackTrace();
            } catch (OperationFailureException e) {
                e.printStackTrace();
                this.lastError = "连接失败" + e.getVendorMessage() + e.getResults().toString();
            }
        }
        return false;
    }

    /**
     * 释放
     */
    public void dispose() {
        try {
            if (readers != null) {
                Log.d(TAG, "dispose: ");
                reader = null;
                readers.Dispose();
                readers = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void ConfigureReader() throws OperationFailureException {
        if (reader != null && reader.isConnected()) {
            TriggerInfo triggerInfo = new TriggerInfo();
            triggerInfo.StartTrigger.setTriggerType(START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE);
            triggerInfo.StopTrigger.setTriggerType(STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE);
            try {
                // receive events from reader
                reader.Events.addEventsListener(this);
                // HH event
                reader.Events.setHandheldEvent(true);
                // tag event with tag data
                reader.Events.setTagReadEvent(true);
                reader.Events.setAttachTagDataWithReadEvent(true);
                // set trigger mode as rfid so scanner beam will not come
                reader.Config.setTriggerMode(ENUM_TRIGGER_MODE.RFID_MODE, true);
                // set start and stop triggers
                reader.Config.setStartTrigger(triggerInfo.StartTrigger);
                reader.Config.setStopTrigger(triggerInfo.StopTrigger);
                // power levels are index based so maximum power supported get the last one
                int MAX_POWER = reader.ReaderCapabilities.getTransmitPowerLevelValues().length - 1;
                // set antenna configurations
                Antennas.AntennaRfConfig config = reader.Config.Antennas.getAntennaRfConfig(1);
                config.setTransmitPowerIndex(MAX_POWER);
                config.setrfModeTableIndex(0);
                config.setTari(0);
                reader.Config.Antennas.setAntennaRfConfig(1, config);
                // Set the singulation control
                Antennas.SingulationControl s1_singulationControl = reader.Config.Antennas.getSingulationControl(1);
                s1_singulationControl.setSession(SESSION.SESSION_S0);
                s1_singulationControl.Action.setInventoryState(INVENTORY_STATE.INVENTORY_STATE_A);
                s1_singulationControl.Action.setSLFlag(SL_FLAG.SL_ALL);
                reader.Config.Antennas.setSingulationControl(1, s1_singulationControl);
                // delete any prefilters
                reader.Actions.PreFilters.deleteAll();

            } catch (InvalidUsageException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 读事件通知
     *
     * @param rfidReadEvents
     */
    @Override
    public void eventReadNotify(RfidReadEvents rfidReadEvents) {
        Log.d(TAG, "eventReadNotify: ");
        if (rfidReadEvents.getReadEventData() != null) {
            Events.ReadEventData readData = rfidReadEvents.getReadEventData();
            if (readData.tagData != null) {
                TagData tagData = readData.tagData;
                Log.d(TAG, "eventReadNotify: " + tagData.getTagID());
                if (!tagData.getTagID().equals(this.tagId)) {
                    this.tagId = tagData.getTagID();
                    this.data = "";
                    if (tagData.getMemoryBankData() != null) {
                        this.data = tagData.getMemoryBankData();
                    }
                    Log.d(TAG, "eventReadNotify: data:" + this.data);
                    handleNotify(1);
                }
            }
        }
    }

    /**
     * 状态事件通知
     *
     * @param rfidStatusEvents
     */
    @Override
    public void eventStatusNotify(RfidStatusEvents rfidStatusEvents) {
        Log.d(TAG, "Status Notification: " + rfidStatusEvents.StatusEventData.getStatusEventType());
        if (rfidStatusEvents.StatusEventData.getStatusEventType() == STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT) {
            if (rfidStatusEvents.StatusEventData.HandheldTriggerEventData.getHandheldEvent() == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED) {
                this.lastError = "";
                this.tagId = "";
                performInventory();
            }
            if (rfidStatusEvents.StatusEventData.HandheldTriggerEventData.getHandheldEvent() == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED) {
                stopInventory();
            }
        }
    }

    /**
     * 触发读卡
     */
    synchronized void performInventory() {
        try {
            if (reader == null || !reader.isConnected()) throw new RuntimeException("读写器未连接");
            reader.Actions.Inventory.perform();
        } catch (InvalidUsageException e) {
            e.printStackTrace();
            this.lastError = "读失败" + e.getInfo() + e.getVendorMessage() + e.getMessage();
        } catch (OperationFailureException e) {
            e.printStackTrace();
            this.lastError = "读失败" + e.getVendorMessage() + e.getResults().toString();
        } catch (Exception e) {
            e.printStackTrace();
            this.lastError = "读失败" + e.getMessage();
        }
    }

    /**
     * 停止读卡
     */
    synchronized void stopInventory() {
        try {
            if (reader == null || !reader.isConnected()) throw new RuntimeException("读写器未连接");
            reader.Actions.Inventory.stop();
        } catch (InvalidUsageException e) {
            e.printStackTrace();
            this.lastError = "读失败" + e.getInfo() + e.getVendorMessage() + e.getMessage();
        } catch (OperationFailureException e) {
            e.printStackTrace();
            this.lastError = "读失败" + e.getVendorMessage() + e.getResults().toString();
        } catch (Exception e) {
            e.printStackTrace();
            this.lastError = "读失败" + e.getMessage();
        }
    }

    void handleNotify(int type) {

        Message message = Message.obtain();
        message.what = type;//0-status,1-tag
        Bundle bundle = new Bundle();
        bundle.putString("data", String.format(template, this.lastError, this.tagId, this.data));
        message.setData(bundle);
        mHandler.sendMessage(message);

    }

}
