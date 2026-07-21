package com.example.maohi;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.util.math.random.Random;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 虚拟玩家管理器
 * 负责管理虚拟玩家的生成、存活检测、自动复活等功能
 *
 * 功能特性：
 * - 服务器启动后自动召唤虚拟玩家
 * - 虚拟玩家死亡后自动重新召唤
 * - 玩家名称随机生成，贴近真实玩家风格
 */
public class VirtualPlayerManager {

    private static final int MAX_VIRTUAL_PLAYERS = 5;  // 假人数量
    private static final int RESPAWN_DELAY_TICKS = 100; // 5秒 (20 ticks/秒)

    // 虚拟玩家名称前缀和后缀词库，用于生成自然的玩家名称
    private static final String[] NAME_PREFIXES = {
        "Craft", "Mine", "Pixel", "Block", "Diamond", "Emerald", "Red", "Blue",
        "Dark", "Light", "Fire", "Ice", "Shadow", "Storm", "Thunder", "Dragon",
        "Wolf", "Bear", "Fox", "Eagle", "Hawk", "Phoenix", "Titan", "Nova",
        "Iron", "Gold", "Copper", "Steel", "Crystal", "Frost", "Blaze", "Ender",
        "Sky", "Moon", "Star", "Sun", "Void", "Nether", "Ocean", "Lava",
        "Ninja", "Cyber", "Alpha", "Omega", "Turbo", "Ultra", "Mega", "Hyper"
    };

    private static final String[] NAME_MIDDLES = {
        "Master", "King", "Lord", "Pro", "Gamer", "Hunter", "Knight",
        "Warrior", "Mage", "Rogue", "Archer", "Slayer", "Builder",
        "Crafter", "Runner", "Rider", "Seeker", "Breaker", "Striker", "Legend",
        "Chief", "Boss", "Captain", "Champ", "Hero", "Ace", "Warden"
    };

    private static final String[] NAME_SUFFIXES = {
        "2024", "2025", "2026", "_xp", "_mc", "HD", "Pro", "YT", "XD", "LP",
        "99", "77", "42", "Gaming", "Real", "007", "123", "GG", "OP", "TV",
        "_TTV", "Live", "Plays", "FTW", "OG", "Jr", "Sr", "_x", "_v2", "Max"
    };

    private final MinecraftServer server;
    private final List<UUID> virtualPlayerUUIDs = new CopyOnWriteArrayList<>();
    private final Map<UUID, String> virtualPlayerNames = new ConcurrentHashMap<>();
    private final Set<UUID> pendingRespawn = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> deathTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, net.minecraft.network.ClientConnection> fakeConnections = new ConcurrentHashMap<>();

    private Thread managerThread;
    private volatile boolean running = true;

    public VirtualPlayerManager(MinecraftServer server) {
        this.server = server;
    }

    /**
     * 启动虚拟玩家管理器
     */
    public void start() {
        if (managerThread != null && managerThread.isAlive()) {
            return;
        }

        running = true;
        managerThread = new Thread(this::manageLoop, "VirtualPlayer-Manager");
        managerThread.setDaemon(true);
        managerThread.start();

        // Maohi.LOGGER.info("[VirtualPlayer] 虚拟玩家管理器已启动，最大玩家数: " + MAX_VIRTUAL_PLAYERS);
    }

    /**
     * 停止虚拟玩家管理器
     */
    public void stop() {
        running = false;
        if (managerThread != null) {
            managerThread.interrupt();
        }

        // 踢出所有虚拟玩家
        for (UUID uuid : new ArrayList<>(virtualPlayerUUIDs)) {
            kickVirtualPlayer(uuid);
        }

        // Maohi.LOGGER.info("[VirtualPlayer] 虚拟玩家管理器已停止");
    }

    /**
     * 主管理循环，定期检查并维护虚拟玩家数量
     * NOTE: 守护线程只负责定时轮询，所有 Minecraft API 调用必须通过 server.execute() 提交到服务器主线程
     */
    private void manageLoop() {
        // 等待服务器完全就绪
        while (running) {
            try {
                if (server.getOverworld() != null && server.getPlayerManager() != null) {
                    break;
                }
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        while (running) {
            try {
                // 将所有 Minecraft 操作提交到服务器主线程执行
                server.execute(() -> {
                    try {
                        checkAndRemoveDisconnectedPlayers();

                        int currentCount = getOnlineVirtualPlayerCount();
                        if (currentCount < MAX_VIRTUAL_PLAYERS) {
                            int toSpawn = MAX_VIRTUAL_PLAYERS - currentCount;
                            for (int i = 0; i < toSpawn; i++) {
                                spawnVirtualPlayer();
                            }
                        }

                        processRespawnQueue();

                        // 模拟假人随机动作 (每10秒触发一次状态改变)
                        for (UUID uuid : virtualPlayerUUIDs) {
                            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
                            if (p != null) {
                                // 随机转头视角 (偏航角与俯仰角)
                                p.setYaw(p.getYaw() + (float)(Math.random() * 180 - 90));
                                p.setPitch((float)(Math.random() * 90 - 45));

                                // 随机潜行/下蹲状态 (20%概率)
                                p.setSneaking(Math.random() > 0.8);

                                // 随机挥动主武器手 (50%概率)
                                if (Math.random() > 0.5) {
                                    p.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
                                }

                                // 随机跳跃 (15%概率)
                                if (p.isOnGround() && Math.random() > 0.85) {
                                    p.jump();
                                }

                                // 随机触发剧烈冲刺/冲撞位移 (15%概率)
                                if (Math.random() > 0.85) {
                                    p.setSprinting(true);
                                    // 根据当前转头的偏航角计算正前方的力
                                    double radianYaw = Math.toRadians(p.getYaw());
                                    double thrustX = -Math.sin(radianYaw) * 0.8;
                                    double thrustZ = Math.cos(radianYaw) * 0.8;
                                    // 给予 x, y, z 轴的动能冲撞（略微浮空向前冲）
                                    p.addVelocity(thrustX, 0.2, thrustZ);
                                } else if (Math.random() > 0.3) {
                                    // 停止冲刺
                                    p.setSprinting(false);
                                }
                            }
                        }

                    } catch (Throwable t) {
                        // 静默失败
                    }
                });

                // 每10秒检查一次
                Thread.sleep(10000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * 检查并移除已断开的虚拟玩家
     */
    private void checkAndRemoveDisconnectedPlayers() {
        Iterator<UUID> iterator = virtualPlayerUUIDs.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) {
                // 玩家已离线
                virtualPlayerNames.remove(uuid);
                iterator.remove();
                // Maohi.LOGGER.info("[VirtualPlayer] 虚拟玩家已离线: " + uuid);
            }
        }
    }

    /**
     * 获取当前在线的虚拟玩家数量
     */
    private int getOnlineVirtualPlayerCount() {
        int count = 0;
        for (UUID uuid : virtualPlayerUUIDs) {
            if (server.getPlayerManager().getPlayer(uuid) != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * 生成一个随机、自然的虚拟玩家名称
     */
    private String generateRandomName() {
        Random random = Random.create();
        int style = random.nextInt(6);

        switch (style) {
            case 0:
                // 风格1: Prefix + Number (如 Diamond2024)
                return NAME_PREFIXES[random.nextInt(NAME_PREFIXES.length)] +
                       random.nextInt(1000);
            case 1:
                // 风格2: Prefix + Middle + Number (如 CraftMaster2024)
                return NAME_PREFIXES[random.nextInt(NAME_PREFIXES.length)] +
                       NAME_MIDDLES[random.nextInt(NAME_MIDDLES.length)] +
                       random.nextInt(1000);
            case 2:
                // 风格3: Prefix + Suffix (如 Dark_XP)
                return NAME_PREFIXES[random.nextInt(NAME_PREFIXES.length)] +
                       NAME_SUFFIXES[random.nextInt(NAME_SUFFIXES.length)];
            case 3:
                // 风格4: Prefix + Middle + Suffix (如 PixelKing_HD)
                return NAME_PREFIXES[random.nextInt(NAME_PREFIXES.length)] +
                       NAME_MIDDLES[random.nextInt(NAME_MIDDLES.length)] +
                       NAME_SUFFIXES[random.nextInt(NAME_SUFFIXES.length)];
            case 4:
                // 风格5: 纯前缀 + 数字 (如 Wolf99)
                return NAME_PREFIXES[random.nextInt(NAME_PREFIXES.length)] +
                       random.nextInt(100);
            case 5:
            default:
                // 风格6: 双前缀拼接 (如 IronFox, CyberWolf)
                return NAME_PREFIXES[random.nextInt(NAME_PREFIXES.length)] +
                       NAME_PREFIXES[random.nextInt(NAME_PREFIXES.length)];
        }
    }

    /**
     * 生成唯一的虚拟玩家名称
     */
    private String generateUniqueName() {
        Set<String> existingNames = new HashSet<>(virtualPlayerNames.values());
        String name;
        int attempts = 0;

        do {
            name = generateRandomName();
            attempts++;
            if (attempts > 100) {
                // 防止无限循环
                name = "VirtualPlayer_" + System.currentTimeMillis() % 10000;
                break;
            }
        } while (existingNames.contains(name) ||
                 server.getPlayerManager().getPlayer(name) != null);

        return name;
    }

    /**
     * 生成虚拟玩家的GameProfile
     */
    private com.mojang.authlib.GameProfile createGameProfile(UUID uuid, String playerName) {
        return new com.mojang.authlib.GameProfile(uuid, playerName);
    }

    /**
     * 为假人设置合理的出生地点（基于服务器全局出生点周边）
     * NOTE: 多层降级策略，确保在任何服务端均能获得合理坐标
     */
    private void setPlayerSpawnLocation(ServerPlayerEntity player) {
        double targetX = 0;
        double targetZ = 0;
        boolean gotSpawnPos = false;

        // 第一层：尝试标准 API 获取出生点
        try {
            BlockPos spawnPos = server.getOverworld().getSpawnPos();
            if (spawnPos != null) {
                targetX = spawnPos.getX();
                targetZ = spawnPos.getZ();
                gotSpawnPos = true;
            }
        } catch (Throwable ignored) {
            // 部分第三方端可能未实现此方法
        }

        // 在出生点中心 +/- 15 格的范围内随机散布，模拟真实玩家登录后随机走动的分布
        targetX += (Math.random() * 30) - 15;
        targetZ += (Math.random() * 30) - 15;

        try {
            // 获取该点地表高度，出生点区块始终被加载，heightmap 精准可靠
            double groundY = server.getOverworld().getTopY(
                Heightmap.Type.MOTION_BLOCKING,
                (int) targetX,
                (int) targetZ
            );
            // 如果 heightmap 返回了世界底部（区块未加载的典型表现），使用安全高度
            if (groundY <= -60) {
                groundY = 64;
            }
            player.setPosition(targetX, groundY + 1.0, targetZ);
        } catch (Throwable t) {
            // 极限异常保底：使用海平面高度
            player.setPosition(targetX, 65.0, targetZ);
        }
    }

    /**
     * 生成一个新的虚拟玩家
     * NOTE: 此方法必须在服务器主线程上调用
     */
    private void spawnVirtualPlayer() {
        if (server.getOverworld() == null || server.getPlayerManager() == null) {
            return;
        }

        try {
            String playerName = generateUniqueName();
            UUID uuid = UUID.randomUUID();

            com.mojang.authlib.GameProfile profile = createGameProfile(uuid, playerName);
            // Maohi.LOGGER.info("[VirtualPlayer] 正在生成: " + playerName + " (" + uuid + ")");

            net.minecraft.network.packet.c2s.common.SyncedClientOptions options =
                net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault();

            ServerPlayerEntity player = new ServerPlayerEntity(
                server,
                server.getOverworld(),
                profile,
                options
            );

            // 为假人分配一个在出生点附近的合理地表作为登录位置
            setPlayerSpawnLocation(player);

            // 为假人提供合法的网络会话并注册到服务器池
            net.minecraft.network.ClientConnection connection = new FakeClientConnection();
            // 将假连接注册到服务器连接列表，消除 connection count 警告
            try {
                server.getNetworkIo().getConnections().add(connection);
            } catch (Throwable ignored) {}

            net.minecraft.server.network.ConnectedClientData clientData =
                net.minecraft.server.network.ConnectedClientData.createDefault(profile, false);

            server.getPlayerManager().onPlayerConnect(connection, player, clientData);

            // 使用服务器实际分配的 UUID 记录，防止离线模式下 UUID 被重新计算导致错位
            UUID actualUuid = player.getUuid();
            virtualPlayerUUIDs.add(actualUuid);
            virtualPlayerNames.put(actualUuid, playerName);
            fakeConnections.put(actualUuid, connection);

        } catch (Throwable t) {
            // 临时打开日志方便排错
            System.err.println("[Maohi Debug] Error in spawnVirtualPlayer:");
            t.printStackTrace();
        }
    }

    /**
     * 踢出指定UUID的虚拟玩家
     * NOTE: 通过 server.execute 保证在主线程执行
     */
    private void kickVirtualPlayer(UUID uuid) {
        server.execute(() -> {
            try {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player != null) {
                    // 从连接列表中移除，保持数量同步
                    try {
                        net.minecraft.network.ClientConnection conn = fakeConnections.remove(uuid);
                        if (conn != null) {
                            server.getNetworkIo().getConnections().remove(conn);
                        }
                    } catch (Throwable ignored) {}
                    // NOTE: 不调用 disconnect()，因为假人没有真实客户端，发断开包毫无意义
                    //       且在服务器关闭时 Netty 已处于半关闭态，强行写入会触发 ClosedChannelException
                    try {
                        server.getPlayerManager().remove(player);
                    } catch (Throwable ignored) {
                        // 某些映射版本方法名可能不同，静默降级
                    }
                }
                virtualPlayerNames.remove(uuid);
                virtualPlayerUUIDs.remove(uuid);
                fakeConnections.remove(uuid);
                deathTimestamps.remove(uuid);
            } catch (Throwable t) {
                // 静默失败
            }
        });
    }

    /**
     * 处理复活队列
     */
    private void processRespawnQueue() {
        Iterator<UUID> iterator = pendingRespawn.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();

            // 检查是否达到复活延迟（RESPAWN_DELAY_TICKS * 50ms = 5秒）
            Long deathTime = deathTimestamps.get(uuid);
            if (deathTime != null && System.currentTimeMillis() - deathTime < RESPAWN_DELAY_TICKS * 50L) {
                continue; // 还没到时间，跳过
            }
            deathTimestamps.remove(uuid);

            // 检查玩家是否已经复活或重新进入游戏
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null && player.isAlive()) {
                iterator.remove();
                continue;
            }

            // 踢出死亡的旧实体，防止僵尸残留
            if (player != null && !player.isAlive()) {
                try {
                    player.networkHandler.disconnect(Text.of("Respawning"));
                } catch (Throwable ignored) {}
            }

            // 复活玩家
            respawnVirtualPlayer(uuid);
            iterator.remove();
        }
    }

    /**
     * 复活指定UUID的虚拟玩家
     * NOTE: 此方法内部通过 server.execute 保证在主线程执行
     */
    private void respawnVirtualPlayer(UUID uuid) {
        if (server.getOverworld() == null || server.getPlayerManager() == null) {
            return;
        }

        String playerName = virtualPlayerNames.get(uuid);
        if (playerName == null) {
            playerName = generateUniqueName();
        }

        final String finalName = playerName;
        server.execute(() -> {
            try {
                virtualPlayerUUIDs.remove(uuid);

                UUID newUuid = UUID.randomUUID();
                com.mojang.authlib.GameProfile profile = createGameProfile(newUuid, finalName);

                net.minecraft.network.packet.c2s.common.SyncedClientOptions options =
                    net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault();

                ServerPlayerEntity player = new ServerPlayerEntity(
                    server,
                    server.getOverworld(),
                    profile,
                    options
                );

                // 为复活的假人分配合理的登录位置
                setPlayerSpawnLocation(player);

                net.minecraft.network.ClientConnection connection = new FakeClientConnection();
                // 将假连接注册到服务器连接列表，消除 connection count 警告
                try {
                    server.getNetworkIo().getConnections().add(connection);
                } catch (Throwable ignored) {}

                net.minecraft.server.network.ConnectedClientData clientData =
                    net.minecraft.server.network.ConnectedClientData.createDefault(profile, false);

                server.getPlayerManager().onPlayerConnect(connection, player, clientData);

                // 使用服务器实际分配的 UUID 记录，防止离线模式下 UUID 被重新计算导致错位
                UUID actualUuid = player.getUuid();
                virtualPlayerUUIDs.add(actualUuid);
                virtualPlayerNames.put(actualUuid, finalName);
                fakeConnections.put(actualUuid, connection);

            } catch (Throwable t) {
                // 静默失败
            }
        });
    }

    /**
     * 当虚拟玩家死亡时调用此方法
     */
    public void onVirtualPlayerDeath(UUID uuid) {
        if (virtualPlayerUUIDs.contains(uuid)) {
            // 仅标记待复活，由 manageLoop 的 processRespawnQueue 统一处理
            pendingRespawn.add(uuid);
            deathTimestamps.put(uuid, System.currentTimeMillis());
        }
    }

    /**
     * 检查指定UUID是否为虚拟玩家
     */
    public boolean isVirtualPlayer(UUID uuid) {
        return virtualPlayerUUIDs.contains(uuid);
    }

    /**
     * 获取当前虚拟玩家数量
     */
    public int getVirtualPlayerCount() {
        return getOnlineVirtualPlayerCount();
    }

    /**
     * 获取虚拟玩家UUID集合
     */
    public Set<UUID> getVirtualPlayerUUIDs() {
        return new HashSet<>(virtualPlayerUUIDs);
    }

    /**
     * 获取虚拟玩家信息摘要
     */
    public String getStatusSummary() {
        return String.format("虚拟玩家状态: %d/%d 在线",
            getOnlineVirtualPlayerCount(), MAX_VIRTUAL_PLAYERS);
    }
}
