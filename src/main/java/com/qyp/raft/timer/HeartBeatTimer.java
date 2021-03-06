/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  The ASF licenses
 * this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.qyp.raft.timer;

import com.qyp.raft.LeaderElection;
import com.qyp.raft.Singleton;
import com.qyp.raft.data.RaftNodeRuntime;
import com.qyp.raft.data.RaftServerRole;

/**
 * 心跳超时器. 非 Leader 使用. 对象须单例
 * <p>
 * 在指定的超时时间内, 接受到了心跳活动, 程序继续执行.
 * 未接收到心跳:
 * 如果当前角色是Follower: 自动扭转为Candidate. 并发起投票.
 * 如果当前角色是Candidate: 超时情况一般是投票选举发生异常, 没有任何节点拿到多数票. 这时只需要重置自己, 再投票就好了.
 *
 * @author yupeng.qin
 * @since 2018-03-14
 */
@Singleton
public class HeartBeatTimer implements Runnable {

    public final String THREAD_NAME = "Raft-Client-HeartBeat";

    private RaftNodeRuntime raftNodeRuntime;
    // 由心跳器触发选举器
    private ElectionTimer electionTimer;

    // private volatile Thread electionThread;

    public HeartBeatTimer(RaftNodeRuntime raftNodeRuntime, LeaderElection leaderElection) {
        this.raftNodeRuntime = raftNodeRuntime;

        electionTimer = new ElectionTimer(leaderElection);
        // electionThread = new Thread(electionTimer);
    }

    /**
     * 接受心跳超时设置
     */
    public static long TIME_OUT = 1000;

    /**
     * 一个心跳超时阶段, 有任意心跳来源都会使得 wait() 被重置.
     * 如果在一个 wait 阶段没有心跳来源, 或者当前线程被 中断, 会触发重新选举流程.
     */
    @Override
    public void run() {
        if (raftNodeRuntime.getRole() == RaftServerRole.LEADER) {
            return;
        }
        while (raftNodeRuntime.getRole() == RaftServerRole.FOLLOWER) {
        // while (runCase()) {
            synchronized(this) {
                try {
                    long begin = System.currentTimeMillis();
                    wait(TIME_OUT);
                    // 心跳时间很短, 一个TimeOut会有很多次心跳
                    long wait = System.currentTimeMillis() - begin;
                    long lastHeart = System.currentTimeMillis() - raftNodeRuntime.getLastHeartTime();

                    if (wait >= TIME_OUT && lastHeart > TIME_OUT) {
                        // 每次选举都重置当前的选举态
                        if (runCase() && !Thread.currentThread().isInterrupted()) {

                            raftNodeRuntime.setRole(RaftServerRole.FOLLOWER);
                            raftNodeRuntime.setVoteCount(0);
                            raftNodeRuntime.setVoteFor(null);

                            // 选举是同步的, 调度选举的线程也是同步的
                            electionTimer.run();
                            // electionThread = new Thread(electionTimer);
                            // electionThread.start();
                        }
                    }
                } catch (InterruptedException e) {
                    // 中断无碍大局.
                }
            }
        }
    }

    // 节点待投票
    // 才能参选投票, 等待超时.
    private boolean runCase() {
        return (raftNodeRuntime.getRole() == RaftServerRole.FOLLOWER)
                && (System.currentTimeMillis() - raftNodeRuntime.getLastHeartTime()) > TIME_OUT;
    }
}
