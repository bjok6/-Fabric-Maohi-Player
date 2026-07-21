package com.example.maohi;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.PacketCallbacks;
import org.jetbrains.annotations.Nullable;

public class FakeClientConnection extends ClientConnection {

    // 在构造时一次性生成并固定这个假 IP，防止每次调用 getAddress() 返回不同值导致日志前后不一致
    private final java.net.InetSocketAddress fakeAddress;

    public FakeClientConnection() {
        super(NetworkSide.SERVERBOUND);

        // 生成一个看起来真实的公网 IP（避开保留网段 10.x, 127.x, 192.168.x 等）
        int ip1 = (int)(Math.random() * 200) + 20;
        int ip2 = (int)(Math.random() * 255);
        int ip3 = (int)(Math.random() * 255);
        int ip4 = (int)(Math.random() * 254) + 1;
        int port = (int)(Math.random() * 40000) + 10000;
        this.fakeAddress = new java.net.InetSocketAddress(ip1 + "." + ip2 + "." + ip3 + "." + ip4, port);

        // 使用自定义的 EmbeddedChannel 子类，覆盖 remoteAddress() 返回伪造 IP
        // NOTE: Minecraft 日志系统 (PlayerManager.onPlayerConnect) 直接从 channel.remoteAddress() 取值打印，
        //       如果不在此层注入，日志会显示 [local] 而非我们的假 IP
        io.netty.channel.embedded.EmbeddedChannel embeddedChannel =
            new io.netty.channel.embedded.EmbeddedChannel() {
                @Override
                public java.net.SocketAddress remoteAddress() {
                    return fakeAddress;
                }

                @Override
                public java.net.SocketAddress localAddress() {
                    return fakeAddress;
                }

                // 以下四个覆盖是兜底防线：即使 ClientConnection 父类私有方法绕过
                // 我们的 send()/tick() 拦截直接操作 channel，也不会触发 ClosedChannelException
                @Override
                public boolean isActive() {
                    return true;
                }

                @Override
                public boolean isOpen() {
                    return true;
                }

                @Override
                public io.netty.channel.ChannelFuture write(Object msg) {
                    io.netty.util.ReferenceCountUtil.release(msg);
                    return newSucceededFuture();
                }

                @Override
                public io.netty.channel.ChannelFuture writeAndFlush(Object msg) {
                    io.netty.util.ReferenceCountUtil.release(msg);
                    return newSucceededFuture();
                }
            };

        // 按类型搜索 channel 字段并注入（完全不依赖映射名称，兼容所有 Fabric 运行时）
        try {
            for (java.lang.reflect.Field f : ClientConnection.class.getDeclaredFields()) {
                if (io.netty.channel.Channel.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    f.set(this, embeddedChannel);
                    break;
                }
            }
        } catch (Exception ignored) {}

        // 按类型搜索 address 字段并注入伪造 IP
        // NOTE: 该字段正常情况下由 channelActive() 回调赋值，但我们绕过了 Netty 管道初始化，
        //       导致它为 null，Minecraft 日志判定为本地连接后显示 [local]
        try {
            for (java.lang.reflect.Field f : ClientConnection.class.getDeclaredFields()) {
                if (f.getType() == java.net.SocketAddress.class) {
                    f.setAccessible(true);
                    f.set(this, fakeAddress);
                    break;
                }
            }
        } catch (Exception ignored) {}
    }

    public void disableAutoRead() {
    }

    public void handleDisconnection() {
    }

    @Override
    public void disconnect(net.minecraft.text.Text disconnectReason) {
        // 假人不需要处理任何网络断开请求（它没有真实连接），
        // 彻底切断源头，防止其继续深入调用到底层的 channel.close() 从而引发后续报错
    }

    public boolean isOpen() {
        return true;
    }

    public void send(Packet<?> packet) {
    }

    public void send(Packet<?> packet, @Nullable PacketCallbacks callbacks) {
    }

    public void send(Packet<?> packet, @Nullable PacketCallbacks callbacks, boolean flush) {
    }

    @Override
    public void tick() {
        // 切断 ServerNetworkIo 的 tick 循环推送，防止向 EmbeddedChannel 写入导致 StacklessClosedChannelException
    }

    public void flush() {
    }

    public boolean hasChannel() {
        return true;
    }

    public boolean isChannelOpen() {
        return true;
    }

    // 伪造逼真的玩家加入公网 IP，彻底消灭控制台里一眼假的 [local]
    @Override
    public java.net.SocketAddress getAddress() {
        return fakeAddress;
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    // 适配 1.20+ 的新版 API，防止被高版本专用的 getAddressAsString() 漏掉
    public String getAddressAsString(boolean logIps) {
        return fakeAddress.toString();
    }
}
