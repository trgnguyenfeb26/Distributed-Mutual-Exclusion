/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import java.io.Serializable;
import java.util.StringTokenizer;

/**
 *
 * @author asus
 */
public class CInterProcessMessage implements Serializable{
    public enum EMessageType{
        MT_REQUEST,MT_REPLY,MT_NOTHING,MT_CS_ENTRY,MT_CS_EXIT,MT_CS_MISSION_COMPLETE,MT_KILL
    };
    public enum EMessageDirection
    {
      MD_SEND,MD_RECEIVE  
    };

    public static final int MESSAGE_SECTION_PART = 4;
    private int m_nSourceProcessId;
    private int m_nDestinationProcessId;
    private CTimeStamp m_timeStamp;
    private EMessageType m_eMsgType;

    public CInterProcessMessage()
    {
        m_nSourceProcessId = -1;
        m_nDestinationProcessId = -1;
        m_timeStamp = new CTimeStamp(-1,-1);
        m_eMsgType = EMessageType.MT_NOTHING;       
    }
    public CInterProcessMessage(int x_nSourceProcessId, int x_nDestinationProcessId, CTimeStamp x_timeStamp, EMessageType x_eMsgType) {
        Set(x_nSourceProcessId,x_nDestinationProcessId,x_timeStamp,x_eMsgType);
    }
    public String ToString()
    {
        return m_nSourceProcessId+" "+m_nDestinationProcessId+" "+m_timeStamp.GetTime()+" "+m_eMsgType;
    }
    public void SetFromStringSections(String x_str1,String x_str2,String x_str3,String x_str4)
    {
        this.m_nSourceProcessId = Integer.parseInt(x_str1);
        this.m_nDestinationProcessId = Integer.parseInt(x_str2);
        this.m_timeStamp.SetTimeStamp(Integer.parseInt(x_str3));
        this.m_timeStamp.SetProcess(m_nSourceProcessId);
        this.m_eMsgType = EMessageType.valueOf(x_str4);        
    }
    public void Set(int x_nSourceProcessId, int x_nDestinationProcessId, CTimeStamp x_timeStamp, EMessageType x_eMsgType)
    {
        this.m_nSourceProcessId = x_nSourceProcessId;
        this.m_nDestinationProcessId = x_nDestinationProcessId;
        this.m_timeStamp = x_timeStamp;
        this.m_eMsgType = x_eMsgType;        
    }
    public CTimeStamp GetTimeStamp()
    {
        return m_timeStamp;
    }
    public int GetSourceProcessId()
    {
        return m_nSourceProcessId;
    }
    public int GetDestinationProcessId()
    {
        return m_nDestinationProcessId;
    }
    public EMessageType GetMessageType()
    {
        return m_eMsgType;
    }
    public void log(EMessageDirection x_eDir)
    {
        if(x_eDir == EMessageDirection.MD_SEND)
            System.out.println("[SEND] "+ m_eMsgType + ": P"+m_nSourceProcessId+"->P"+m_nDestinationProcessId+" TIME_STAMP("+m_timeStamp.GetTime()+","+m_timeStamp.GetProcessId()+")");
        if(x_eDir == EMessageDirection.MD_RECEIVE)
            System.out.println("[RECV] "+ m_eMsgType + ": P"+m_nSourceProcessId+"->P"+m_nDestinationProcessId+" TIME_STAMP("+m_timeStamp.GetTime()+","+m_timeStamp.GetProcessId()+")");
    }
    
    
    
    
}
