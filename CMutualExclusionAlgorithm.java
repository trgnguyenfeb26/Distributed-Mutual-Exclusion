/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *  
 * 
 */
public class CMutualExclusionAlgorithm {
    public static void main(String[] args) throws InterruptedException {
        //Check to make sure if 2 parameters have been entered.
        //Two parameters are Process number and TimeUnit(according to TA's requirement)
        // if(args.length != 2)
        // {
        //     System.out.println("Invalid Parameters");
        //     System.exit(0);
        // }
       int nProcessId = Integer.parseInt(args[0]);
       int nTimeUnit = Integer.parseInt(args[1]);
        //  int nProcessId = 0;
        //  int nTimeUnit = 200;
        
        // Creating and initializing network layer.
        // Network layer is an upper level for my tcp links to hide their complexities.
        // It is initialized by the list of IP and Ports in conf.txt
        CNetworkLayer network = new CNetworkLayer(nProcessId,false);
        if(!network.InitFromFile("conf.txt"))
        {
            System.out.println("failed to Initialize network layer");
            System.exit(-1);
        }
        System.out.println("Started...");
        // After initializing Sockets, each node tries to connect itself to other 9 processes.
        network.EstablishAllConnections();
        Thread.sleep(100);
        // This is Algorithm implementation
        CMutualExclusionManager MEManager = new CMutualExclusionManager(nProcessId, network, nTimeUnit);
        // Enough time between two consecutive trying to enter Critical Section
        boolean bEnoughTimeElapsed = false;
        CStopwatch stop_watch = new CStopwatch();
        
        // main loop of program
        while(!MEManager.ShallIExit())
        {
            // System.out.println("bEnoughTimeElapsed : "+bEnoughTimeElapsed);
            if(!bEnoughTimeElapsed) // Its not still time to next try
            {
                System.out.println("next try");
                if(!stop_watch.IsStarted())
                {
                    System.out.println("stop_watch : "+stop_watch.IsStarted()+"   ");
                    stop_watch.Start();
                }
                else
                {
                    //Enough time past. So in next loop you may try...
                    System.out.println("stop_watch : "+stop_watch.IsStarted()+"   "+stop_watch.elapsedTime()+" - "+MEManager.CalculateTheTimeBetweenTwoConsecutiveTry());
                    if(stop_watch.elapsedTime() >= MEManager.CalculateTheTimeBetweenTwoConsecutiveTry())
                    {
                        bEnoughTimeElapsed = true;
                    }
                }                
            }
            else // Now you may Try... 
            {
                System.out.println("Loading...");
                if(!MEManager.IsMissionComplete()) // If this process 40 times Successfuly entered CS? 
                {
                    //Send request only one time and then change your state to PS_TRYING
                    if(MEManager.GetProcessState() == CMutualExclusionManager.ProcessState.PS_NONCRITICAL)
                    {
                        MEManager.Request();
                    }
                    //ASK the logic of algorithm if you are allowed to enter CS
                    if(MEManager.AmIAllowedToEnterCS())
                    {
                        MEManager.CriticalSection();
                        bEnoughTimeElapsed = false;
                        stop_watch.Stop();
                    }
                }
                else // Kill All the Processess and exit
                {
                    //As node 0 if all other processes already sent their mission completion message,
                    // sends all of them Kill signal.
                    if(MEManager.AllProcessFinished())
                    {
                        System.out.println("Kill...");
                        MEManager.KillAll();
                        Thread.sleep(500);
                        // Flush the Log files and then exit
                    }
                }
                
            }
            // each time I only sleep a short time, and measure the past time in a non blocking manner
            // to let my communications work
            Thread.sleep(MEManager.GetTimeUnit());
        }
        
        // Log data and flush the data into file before closing the app.
        MEManager.LogData();
        // Close the sockets and threads
        network.Close();
            
    }
}
