/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Observer;
import java.util.Observable;
/**
 *
 * 
 */
public class CNetworkLayer extends Observable implements Runnable {
    private int                                 m_nProcessId;
    private CTCPListener                        m_Listener;
    private Map<Integer, CTCPLinkClient>        m_Links;
    //private Map<Integer, Boolean>               m_hashReceivedIdentity;
    private boolean[]                           m_bHandShakeDone;
    private Semaphore                           m_mutex; 
    private boolean                             m_bTestEnabled;
    private volatile boolean                    m_bExit;

    public CNetworkLayer(int x_nProcessId,boolean x_bTestEnabled)
    {
        m_nProcessId = x_nProcessId;
        m_Links = new HashMap<Integer, CTCPLinkClient>(CMutualExclusionManager.MAX_PROCESS_NUMBER - 1);
        //m_hashReceivedIdentity = new HashMap<Integer, Boolean>(CMutualExclusionManager.MAX_PROCESS_NUMBER - 1);
        m_bHandShakeDone = new boolean[CMutualExclusionManager.MAX_PROCESS_NUMBER];
        for(int i = 0 ; i < CMutualExclusionManager.MAX_PROCESS_NUMBER ; i++)
        {
            if(i == m_nProcessId)
                m_bHandShakeDone[i] = true;
            else
                m_bHandShakeDone[i] = false;
        }
        m_mutex = new Semaphore(1);
        m_bTestEnabled = x_bTestEnabled;
        m_bExit = false;           
    }
    // All the processes create their sockes based on a configuration file(conf.txt)
    // It creates the server as well as 9 clients
    public boolean InitFromFile(String x_strConfigPath)
    {
        // Takes a configuration file and returns called entries
        Properties p = new Properties(); 
        try
        {
            p.load(new FileInputStream(x_strConfigPath));
            for(int i = 0 ; i < CMutualExclusionManager.MAX_PROCESS_NUMBER ; i++) 
            {
                String strIP   = p.getProperty("IP_P"+i);
                int nPort = Integer.parseInt(p.getProperty("PORT_P"+i));
                int nAttempt = 0;
                if(m_nProcessId == i) // This is My Server Socket
                {
                    m_Listener = new CTCPListener(strIP,nPort);
                    m_Listener.start();
                }
                else //These are other processess' server info.
                {
                    //m_hashReceivedIdentity.put(i, Boolean.FALSE);
                    CTCPLinkClient clnt = new CTCPLinkClient(strIP, nPort, m_nProcessId, i);
                    while(!clnt.Initialize())
                    {
                        try 
                        {
                            Thread.sleep(1000);
                        }
                        catch (InterruptedException ex)
                        {
                            ex.printStackTrace();
                        }
                        //System.out.println("Attempt "+nAttempt+" to initialize Socket "+strIP+":"+nPort );
                        nAttempt++;
                    }
                    System.out.println("[OK] Socket "+strIP+":"+nPort );
                    m_Links.put(i, clnt);
                }                
            }
            if(m_bTestEnabled)
            {
                String strIP99 = p.getProperty("IP_P99");
                int nPort99 = Integer.parseInt(p.getProperty("PORT_P99"));
                CTCPLinkClient clnt_monitor = new CTCPLinkClient(strIP99, nPort99, m_nProcessId, 99);
                while(!clnt_monitor.Initialize())
                {
                    try 
                    {
                        Thread.sleep(1000);
                    }
                    catch (InterruptedException ex)
                    {
                        ex.printStackTrace();
                    }
                }
                System.out.println("[OK] Socket "+strIP99+":"+nPort99 );
                m_Links.put(99,clnt_monitor);
            }
            
        }
        catch (IOException ex) 
        {
            Logger.getLogger(CNetworkLayer.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        Thread receive_thread = new Thread(this);
        receive_thread.start();
        return true;
    }
    // Node 0 sends this message to all other processes. Upon receiving a similar message from
    // them, it stops sending MT_NOTHING and sets a flag in m_bHandShakeDone indicating that the corresponding node 
    // has been connected and is ready
    public void EstablishAllConnections()
    {
        //System.out.println("Waiting for other neighbors...");
        if(m_nProcessId != 0)
            return;
        try
        {
            while(!AllConnected())
            {
                for(int i = 1 ; i < CMutualExclusionManager.MAX_PROCESS_NUMBER ; i++)
                {
                    m_mutex.acquire();
                    if(!m_bHandShakeDone[i])
                        REQUEST_handshake_Packet(i);
                    m_mutex.release();
                }
                Thread.sleep(25);
            }
        }
        catch (InterruptedException ex) 
        {
        
        }
        //System.out.println("All Connections Established...");
    }
    // Node 0 sends this message to all other processes. Upon receiving a similar message from
    // them, it stops sending MT_NOTHING and sets a flag in m_bHandShakeDone indicating that the corresponding node 
    // has been connected and is ready
    public void REQUEST_handshake_Packet(int x_nDest)
    {
        m_Links.get(x_nDest).Send_Identification_Packet();
    }
    // called by nodes other than 0 in response of the similar message
    public void RESPONSE_handshake_Packet()
    {
        m_Links.get(0).Send_Identification_Packet();
    }
    // it returns true, if all the nodes have been connected to the current process
    public boolean AllConnected()
    {
        boolean bRet = true;
        try {
            m_mutex.acquire();
            for(int i = 0 ; i < CMutualExclusionManager.MAX_PROCESS_NUMBER ; i++)
            {
                if(!m_bHandShakeDone[i])
                    bRet = false;
            }
            m_mutex.release();
            
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
        return bRet;
    }

    public void SendMessage(CInterProcessMessage x_msg)
    {
        int nDestProcessId = x_msg.GetDestinationProcessId();
        if((nDestProcessId != 99) && (nDestProcessId < 0 || nDestProcessId >= CMutualExclusionManager.MAX_PROCESS_NUMBER))
        {
            System.out.println("SendMessage: Invalid Destination Process "+nDestProcessId);
            System.exit(-1);
        }
        m_Links.get(nDestProcessId).Send(x_msg);
    }
    private boolean hasMessage()
    {
        return m_Listener.HasMessage();
    }
    private ArrayList<CInterProcessMessage> getMessages()
    {
        return m_Listener.GetMessages();
    }
    private ArrayList<CInterProcessMessage> getHandShakeMessages()
    {
        return m_Listener.GetHandShakeMessages();
    }
    
    public void run()
    {
        while(!m_bExit)
        {
            try
            {
                Thread.sleep(5);
                if(hasMessage())
                {
                    ArrayList<CInterProcessMessage> arr_msgs;
                    arr_msgs = getMessages();
                    for (CInterProcessMessage m : arr_msgs)
                    {
                        if(m.GetMessageType() == CInterProcessMessage.EMessageType.MT_NOTHING)
                        {
                            //m.log(CInterProcessMessage.EMessageDirection.MD_RECEIVE);
                            
                            if(m_nProcessId == 0)
                            {
                                m_mutex.acquire();
                                m_bHandShakeDone[m.GetSourceProcessId()] = true;
                                m_mutex.release();    
                            }
                            else
                            {
                                RESPONSE_handshake_Packet();
                            }                            
                        }
                        else
                        {
                            setChanged();
                            notifyObservers(m);
                        }
                    }    
                }                
            }
            catch(InterruptedException ex)
            {
                ex.printStackTrace();
            }
        }
    }
    // Shuts down all the links(including server and client sockets) and the threads
    public void Close()
    {
        m_bExit = true;
        m_Listener.Stop(); 
       
        for(int i = 0 ; i < CMutualExclusionManager.MAX_PROCESS_NUMBER ; i++)
        {
            if(m_Links.get(i) != null)
                m_Links.get(i).Stop();
        }
        if(m_bTestEnabled)
        {
            if(m_Links.get(99) != null)
                m_Links.get(99).Stop();
        }
    }
}
