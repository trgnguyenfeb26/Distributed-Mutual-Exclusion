/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Observable;
import java.util.concurrent.Semaphore;
import java.util.Observer;
import java.util.Observable;
/**
* This object is responsible for accepting connections from other processes by a welcoming tcp socket.
* And once it accepts a new connection, tcp will create a new socket and this object, will create a new thread and pass the socket to it.
* Also, this class implements Observer Pattern. In children object(CTCPLinkServer) once a message received,it will notify its parent which is
* this object(CTCPListener. And this object pushes the incoming messages at the end of a queue(m_lstInbox)

*/

public class CTCPListener extends Thread implements Observer{
    
    private ServerSocket            m_WelcomingSocket;
    private String                  m_strHostIP;
    private int                     m_nHostPort;
    ArrayList<CInterProcessMessage> m_lstInbox;
    Semaphore                       m_mutex;
    private volatile boolean        m_bExit;
    public CTCPListener(String x_strHostIP,int x_nHostPort)
    {
        try
        {
            m_strHostIP = x_strHostIP;
            m_nHostPort = x_nHostPort;
            m_WelcomingSocket = new ServerSocket(m_nHostPort,0,InetAddress.getByName(m_strHostIP));
            m_lstInbox = new ArrayList<CInterProcessMessage>();
            m_bExit = false;
            m_mutex = new Semaphore(1);
        }
        catch (IOException ex) 
        {
           ex.printStackTrace();
        }
    }
    // notify handler(Observer design pattern)
    // Since m_lstInbox are manipulated in different threads I secured it with a mutex
    public void update(Observable obj,Object arg)
    {
        if(arg instanceof CInterProcessMessage)
        {
            try 
            {
                m_mutex.acquire();
                m_lstInbox.add((CInterProcessMessage)arg);
                //((CInterProcessMessage)arg).log(CInterProcessMessage.EMessageDirection.MD_RECEIVE);
                m_mutex.release();
            }
            catch (InterruptedException ex) 
            {
               ex.printStackTrace();
            }
        }
        else
        {
            System.out.println("Invalid Serialized Message Received...");
            System.exit(0);
        }
    }
    // Stops this object by closing the welcoming socket and the thread.
    // Must be called at the end
    public void Stop()
    {
        try
        {
            m_bExit = true;
            m_WelcomingSocket.close();
        }
        catch(IOException ex)
        {
            
        }
    }
    // Listener Thread blocks on new connections. Everytime receives a new connection,
    // creates a new thread and pass the newly created socket to it. Also, registers itself as the listener of 
    // new messages to be notified by the child(Observer pattern)
    public void run()
    {
        //System.out.println("server Started To Listening...");
        int nLinkNumber = 0;
        ArrayList<Thread> arrThreadList = new ArrayList<Thread>();
        try
        {
            while(!m_bExit)
            {
                Socket sock_clnt = m_WelcomingSocket.accept();
                if(sock_clnt != null)
                {
                    CTCPLinkServer server_tcp_link = new CTCPLinkServer(nLinkNumber++,sock_clnt);
                    server_tcp_link.addObserver(this);
                    Thread sock_thread = new Thread(server_tcp_link);
                    arrThreadList.add(sock_thread);
                    sock_thread.start();
                }
            }
            for (Thread thr : arrThreadList)
            {
                thr.interrupt();
            }
        }
        catch(IOException ex)
        {
            //ex.printStackTrace();
        }
    }
    //Any new messages to deliver to upper layer(network layer)
    public boolean HasMessage()
    {
        return (m_lstInbox.size() > 0);
    }
    //Only delivers handshake messages (these messages are used only during initialization phase)
    public ArrayList<CInterProcessMessage> GetHandShakeMessages()
    {
        ArrayList<CInterProcessMessage> arrBuffer = new ArrayList<CInterProcessMessage>();
        try
        {
            m_mutex.acquire();
            for(Iterator<CInterProcessMessage> it = m_lstInbox.iterator() ; it.hasNext();)
            {
                CInterProcessMessage msg = it.next();
                if(msg.GetMessageType() == CInterProcessMessage.EMessageType.MT_NOTHING)
                {
                    arrBuffer.add(msg);
                    it.remove();
                }
            }
            m_mutex.release();

        }
        catch(InterruptedException ex)
        {
            ex.printStackTrace();
        }
        return arrBuffer;
    }
    // Delivers Mutual exclusion messages(Req and Rep)
    // Again, since it is accessed by different threads(network layer and CTCPLinkServer)
    // I used mutex to secure it.
    public ArrayList<CInterProcessMessage> GetMessages()
    {
        ArrayList<CInterProcessMessage> arrBuffer = new ArrayList<CInterProcessMessage>();
        try
        {
            m_mutex.acquire();
            Iterator<CInterProcessMessage> it = m_lstInbox.iterator();
            while(it.hasNext())
            {
                arrBuffer.add(it.next());
            }
            m_lstInbox.clear();
            m_mutex.release();
        }
        catch(InterruptedException ex)
        {
            ex.printStackTrace();
        }
        return arrBuffer;            
    }    
}
