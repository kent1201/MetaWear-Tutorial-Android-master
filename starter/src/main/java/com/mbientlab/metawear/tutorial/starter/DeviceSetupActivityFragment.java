/*
 * Copyright 2015 MbientLab Inc. All rights reserved.
 *
 * IMPORTANT: Your use of this Software is limited to those specific rights
 * granted under the terms of a software license agreement between the user who
 * downloaded the software, his/her employer (which must be your employer) and
 * MbientLab Inc, (the "License").  You may not use this Software unless you
 * agree to abide by the terms of the License which can be found at
 * www.mbientlab.com/terms . The License limits your use, and you acknowledge,
 * that the  Software may not be modified, copied or distributed and can be used
 * solely and exclusively in conjunction with a MbientLab Inc, product.  Other
 * than for the foregoing purpose, you may not use, reproduce, copy, prepare
 * derivative works of, modify, distribute, perform, display or sell this
 * Software and/or its documentation for any purpose.
 *
 * YOU FURTHER ACKNOWLEDGE AND AGREE THAT THE SOFTWARE AND DOCUMENTATION ARE
 * PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESS OR IMPLIED,
 * INCLUDING WITHOUT LIMITATION, ANY WARRANTY OF MERCHANTABILITY, TITLE,
 * NON-INFRINGEMENT AND FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT SHALL
 * MBIENTLAB OR ITS LICENSORS BE LIABLE OR OBLIGATED UNDER CONTRACT, NEGLIGENCE,
 * STRICT LIABILITY, CONTRIBUTION, BREACH OF WARRANTY, OR OTHER LEGAL EQUITABLE
 * THEORY ANY DIRECT OR INDIRECT DAMAGES OR EXPENSES INCLUDING BUT NOT LIMITED
 * TO ANY INCIDENTAL, SPECIAL, INDIRECT, PUNITIVE OR CONSEQUENTIAL DAMAGES, LOST
 * PROFITS OR LOST DATA, COST OF PROCUREMENT OF SUBSTITUTE GOODS, TECHNOLOGY,
 * SERVICES, OR ANY CLAIMS BY THIRD PARTIES (INCLUDING BUT NOT LIMITED TO ANY
 * DEFENSE THEREOF), OR OTHER SIMILAR COSTS.
 *
 * Should you have any questions regarding your right to use this Software,
 * contact MbientLab Inc, at www.mbientlab.com.
 */

package com.mbientlab.metawear.tutorial.starter;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;

import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.builder.RouteBuilder;
import com.mbientlab.metawear.builder.RouteComponent;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.data.AngularVelocity;
import com.mbientlab.metawear.data.EulerAngles;
import com.mbientlab.metawear.data.Quaternion;
import com.mbientlab.metawear.impl.platform.IO;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.GyroBmi160;
import com.mbientlab.metawear.module.Led;
import com.mbientlab.metawear.module.SensorFusionBosch;

import bolts.Continuation;
import bolts.Task;

/**
 * A placeholder fragment containing a simple view.
 */
public class DeviceSetupActivityFragment extends Fragment implements ServiceConnection {
    public interface FragmentSettings {
        BluetoothDevice getBtDevice();
    }

    private MetaWearBoard metawear;
    private FragmentSettings settings;
    private Accelerometer accelerometer;
    private GyroBmi160 gyro_bmi160;
    private SensorFusionBosch sensorFusion;
//    private MagnetometerBmm150  magnetometerBmm150;


    public DeviceSetupActivityFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Activity owner= getActivity();
        if (!(owner instanceof FragmentSettings)) {
            throw new ClassCastException("Owning activity must implement the FragmentSettings interface");
        }

        settings= (FragmentSettings) owner;
        owner.getApplicationContext().bindService(new Intent(owner, BtleService.class), this, Context.BIND_AUTO_CREATE);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        ///< Unbind the service when the activity is destroyed
        getActivity().getApplicationContext().unbindService(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setRetainInstance(true);
        return inflater.inflate(R.layout.fragment_device_setup, container, false);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        metawear = ((BtleService.LocalBinder) service).getMetaWearBoard(settings.getBtDevice());
        accelerometer = metawear.getModule(Accelerometer.class);
        accelerometer.configure()
                .odr(100f)       // Set sampling frequency to 25Hz, or closest valid ODR
                .commit();
        gyro_bmi160 = metawear.getModule(GyroBmi160.class);
        gyro_bmi160.configure().odr(GyroBmi160.OutputDataRate.valueOf("ODR_100_HZ")).commit();
        sensorFusion = metawear.getModule(SensorFusionBosch.class);
        sensorFusion.configure()
                .mode(SensorFusionBosch.Mode.NDOF)
                .accRange(SensorFusionBosch.AccRange.AR_2G)
                .gyroRange(SensorFusionBosch.GyroRange.GR_250DPS)
                .commit();
//        barometer_b = metawear.getModule(BarometerBme280.class);
//        barometer_b.configure().standbyTime(50f).commit();
//        magnetometerBmm150 = metawear.getModule(MagnetometerBmm150.class);
//        magnetometerBmm150.configure().outputDataRate(MagnetometerBmm150.OutputDataRate.valueOf("ODR_25_HZ")).commit();


}

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    /**
     * Called when the app has reconnected to the board
     */
    public void reconnected() { }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // LED switch
        ((Switch) view.findViewById(R.id.led_ctrl)).setOnCheckedChangeListener((buttonView, isChecked) -> {
            Led led= metawear.getModule(Led.class);
            if (isChecked) {
                led.editPattern(Led.Color.BLUE, Led.PatternPreset.SOLID)
                        .repeatCount(Led.PATTERN_REPEAT_INDEFINITELY)
                        .commit();
                led.play();
            } else {
                led.stop(true);
            }
        });

        // Accelerometer button
        view.findViewById(R.id.acc_start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final int[] acc_count = {0};
                accelerometer.acceleration().addRouteAsync(new RouteBuilder() {
                    @Override
                    public void configure(RouteComponent source) {
                        source.stream(new Subscriber() {
                            @Override
                            public void apply(Data data, Object... env) {
                                Log.i("MainActivity", "Accelerometer " +acc_count[0]+" : "+data.value(Acceleration.class).toString());
                                acc_count[0]++;
                            }
                        });
                    }
                }).continueWith(new Continuation<Route, Void>() {
                    @Override
                    public Void then(Task<Route> task) throws Exception {
                        accelerometer.acceleration().start();
                        accelerometer.start();
                        return null;
                    }
                });
            }
        });
        view.findViewById(R.id.acc_stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accelerometer.stop();
                accelerometer.acceleration().stop();
                metawear.tearDown();
            }
        });

        // Gyro button
        view.findViewById(R.id.gyr_start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final int[] gyr_count = {0};
                gyro_bmi160.angularVelocity().addRouteAsync(new RouteBuilder() {
                    @Override
                    public void configure(RouteComponent source) {
                        source.stream(new Subscriber() {
                            @Override
                            public void apply(Data data, Object... env) {
                                Log.i("MainActivity", "Gyro " + gyr_count[0] + " : "+data.value(AngularVelocity.class).toString());
                                gyr_count[0]++;
                            }
                        });
                    }
                }).continueWith(new Continuation<Route, Void>() {
                    @Override
                    public Void then(Task<Route> task) throws Exception {
                        gyro_bmi160.angularVelocity().start();
                        gyro_bmi160.start();
                        return null;
                    }
                });
            }
        });
        view.findViewById(R.id.gyr_stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gyro_bmi160.stop();
                gyro_bmi160.angularVelocity().stop();
                metawear.tearDown();
            }
        });

        // Quaternion button
        view.findViewById(R.id.qua_start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final int[] qua_count = {0};
                sensorFusion.quaternion().addRouteAsync(new RouteBuilder() {
                    @Override
                    public void configure(RouteComponent source) {
                        source.limit(33).stream(new Subscriber() {
                            @Override
                            public void apply(Data data, Object... env) {
                                Log.i("MainActivity", "Quaternoin "+ qua_count[0] + " : "+data.value(Quaternion.class).toString());
                                qua_count[0]++;
                            }
                        });
                    }
                }).continueWith(new Continuation<Route, Void>() {
                    @Override
                    public Void then(Task<Route> task) throws Exception {
                        sensorFusion.quaternion().start();
                        sensorFusion.start();
                        return null;
                    }
                });
            }
        });
        view.findViewById(R.id.qua_stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sensorFusion.stop();
                sensorFusion.quaternion().stop();
                metawear.tearDown();
            }
        });

        // EulerAngles button
        view.findViewById(R.id.eua_start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final int[] eua_count = {0};
                sensorFusion.eulerAngles().addRouteAsync(new RouteBuilder() {
                    @Override
                    public void configure(RouteComponent source) {
                        source.limit(33).stream(new Subscriber() {
                            @Override
                            public void apply(Data data, Object... env) {
                                Log.i("MainActivity", "eulerAngles "+ eua_count[0] +" : "+data.value(EulerAngles.class).toString());
                                eua_count[0]++;
                            }
                        });
                    }
                }).continueWith(new Continuation<Route, Void>() {
                    @Override
                    public Void then(Task<Route> task) throws Exception {
                        sensorFusion.eulerAngles().start();
                        sensorFusion.start();
                        return null;
                    }
                });
            }
        });
        view.findViewById(R.id.eua_stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sensorFusion.stop();
                sensorFusion.eulerAngles().stop();
                metawear.tearDown();
            }
        });

//        // Gravity button
//        view.findViewById(R.id.gra_start).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                final int[] gra_start = {0};
//                sensorFusion.gravity().addRouteAsync(new RouteBuilder() {
//                    @Override
//                    public void configure(RouteComponent source) {
//                        source.limit(33).stream(new Subscriber() {
//                            @Override
//                            public void apply(Data data, Object... env) {
//                                Log.i("MainActivity", "gravity " + gra_start[0] + " : "+data.value(Acceleration.class).toString());
//                                gra_start[0]++;
//                            }
//                        });
//                    }
//                }).continueWith(new Continuation<Route, Void>() {
//                    @Override
//                    public Void then(Task<Route> task) throws Exception {
//                        sensorFusion.gravity().start();
//                        sensorFusion.start();
//                        return null;
//                    }
//                });
//            }
//        });
//        view.findViewById(R.id.gra_stop).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                sensorFusion.stop();
//                sensorFusion.gravity().stop();
//                metawear.tearDown();
//            }
//        });

        // linearAcceleration button
        view.findViewById(R.id.lina_start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final int[] lina_count = {0};
                sensorFusion.linearAcceleration().addRouteAsync(new RouteBuilder() {
                    @Override
                    public void configure(RouteComponent source) {
                        source.limit(33).stream(new Subscriber() {
                            @Override
                            public void apply(Data data, Object... env) {
                                Log.i("MainActivity", "linearAcceleration " + lina_count[0] + " : "+data.value(Acceleration.class).toString());
                                lina_count[0]++;
                            }
                        });
                    }
                }).continueWith(new Continuation<Route, Void>() {
                    @Override
                    public Void then(Task<Route> task) throws Exception {
                        sensorFusion.linearAcceleration().start();
                        sensorFusion.start();
                        return null;
                    }
                });
            }
        });
        view.findViewById(R.id.lina_stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sensorFusion.stop();
                sensorFusion.linearAcceleration().stop();
                metawear.tearDown();
            }
        });



    }


}
