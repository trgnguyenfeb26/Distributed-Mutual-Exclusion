/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * This class is in charge of sending messages.
 * 
 */
public class CTCPLinkClient {
    private Socket              m_socket;
    private String              m_strRemoteIP;
    private int                 m_nRemotePort;
    private int                 m_nSourceProcessId;
    private int                 m_nDestProcessId;
    private ObjectOutputStream  m_obj_out_stream;
    //private DataOutputStream    m_data_out_stream;
    private Semaphore           m_mutex;
    public CTCPLinkClient(String x_strIP,int x_nPort,int x_nSourceProcessId,int x_nDestProcessId)
    {
        m_strRemoteIP = x_strIP;
        m_nRemotePort = x_nPort;
        m_nSourceProcessId = x_nSourceProcessId;
        m_nDestProcessId = x_nDestProcessId;
        m_mutex = new Semaphore(1);
        
    }
    public boolean Initialize()
    {
        boolean bRet = false;
        try
        {
            m_socket = new Socket(m_strRemoteIP,m_nRemotePort);
            m_obj_out_stream = new ObjectOutputStream(m_socket.getOutputStream());
            //m_data_out_stream = new DataOutputStream(m_socket.getOutputStream());
            bRet = true;
        }
        catch (IOException ex) 
        {
            //ex.printStackTrace();
        }
        return bRet;
        
    }
    public void Stop()
    {
        try 
        {
            m_socket.close();
        }
        catch (IOException ex)
        {
            
        }
    }
    //Sends a packet with Message type MT_NOTHING. This message is used for initialization handshake to make node 0 sure that all the 
    // other nodes have created their connections and are ready to start entering the critical section
    public void Send_Identification_Packet()
    {
        try
        {
            CInterProcessMessage msg = new CInterProcessMessage(m_nSourceProcessId, m_nDestProcessId, new CTimeStamp(-1,-1), CInterProcessMessage.EMessageType.MT_NOTHING);        
            m_mutex.acquire();
            //m_data_out_stream.writeBytes(msg.ToString()+"\n");
            m_obj_out_stream.writeObject(msg);
            m_mutex.release();
        }
        catch (IOException ex) {
            ex.printStackTrace();
        } catch (InterruptedException ex) {
            Logger.getLogger(CTCPLinkClient.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    // This method is used for REQ and REP messages.
    public void Send(CInterProcessMessage x_msg)
    {
        try
        {
            m_mutex.acquire();
            m_obj_out_stream.writeObject(x_msg);
            //m_data_out_stream.writeBytes(x_msg.ToString()+"\n");
            m_mutex.release();
        }
        catch(IOException ex)
        {
            ex.printStackTrace();
        } catch (InterruptedException ex) {
            Logger.getLogger(CTCPLinkClient.class.getName()).log(Level.SEVERE, null, ex);
        }
    }  
}
