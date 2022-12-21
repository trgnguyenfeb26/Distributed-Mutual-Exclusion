/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


/**
  Lamport Clock implementation
 */
public class CLamportClock {
    //D_VALUE is the incremental positive value of D based on professor's slides
    public static final int D_VALUE = 1;
    private CTimeStamp      m_timestamp;
    private int             m_nProcessId;     

    public CLamportClock(int x_nProcessId) {
        m_nProcessId = x_nProcessId;
        m_timestamp = new CTimeStamp(0,m_nProcessId);
    }
    public CTimeStamp GetTimeStamp()
    {
        return m_timestamp;
    }
    //Second rule. refer to the slides and Lamport paper
    public void OnReceiveNeighborProcessTimestamp(CTimeStamp x_ts)
    {
        ApplySecondRule(x_ts);
    }
    //First rule. refer to the slides and Lamport paper
    public void OnInternalEvent()
    {
        ApplyFirstRule();
    }
    private void ApplyFirstRule()
    {
        int nNewValue = m_timestamp.GetTime();
        nNewValue += D_VALUE;
        m_timestamp.SetTimeStamp(nNewValue);
    }
    // upon receiving a new timestamp, take a maximum of your own and the received one.
    // But before that, first add a D to received one.
    private void ApplySecondRule(CTimeStamp x_ts)
    {
        int nNewClock = x_ts.GetTime() + D_VALUE;
        if(nNewClock > m_timestamp.GetTime())
            m_timestamp.SetTimeStamp(nNewClock);
    }    
}
