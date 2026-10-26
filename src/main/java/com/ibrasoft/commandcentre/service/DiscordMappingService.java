package com.ibrasoft.commandcentre.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateNameEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DiscordMappingService {

    private JDA jda;
    private final Map<String, String> userCache = new ConcurrentHashMap<>();
    private final Map<String, String> roleCache = new ConcurrentHashMap<>();

    @Value("${discord.bot.token}")
    private String botToken;

    @PostConstruct
    public void init() throws InterruptedException {
        this.jda = JDABuilder.createDefault(botToken)
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_PRESENCES)
                .addEventListeners(new CacheListener())
                .build()
                .awaitReady();

        // Initial cache population
        for (Guild guild : jda.getGuilds()) {
            // Load all members (JDA lazy-loads by default)
            guild.loadMembers().onSuccess(members -> {
                for (Member member : members) {
                    userCache.put(member.getId(), member.getEffectiveName());
                }
            });

            for (Role role : guild.getRoles()) {
                roleCache.put(role.getId(), role.getName());
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
        }
    }

    private class CacheListener extends ListenerAdapter {
        @Override
        public void onGuildMemberJoin(GuildMemberJoinEvent event) {
            userCache.put(event.getMember().getId(), event.getMember().getEffectiveName());
        }

        @Override
        public void onGuildMemberUpdateNickname(GuildMemberUpdateNicknameEvent event) {
            userCache.put(event.getMember().getId(),
                    event.getMember().getEffectiveName());
        }

        @Override
        public void onRoleUpdateName(RoleUpdateNameEvent event) {
            roleCache.put(event.getRole().getId(),
                    event.getRole().getName());
        }
    }

    public String getNickname(String userId) {
        String cached = userCache.get(userId);
        if (cached != null) return cached;

        // The startup snapshot misses anyone who joined later, so fetch on a cache miss.
        for (Guild guild : jda.getGuilds()) {
            try {
                Member member = guild.retrieveMemberById(userId).complete();
                userCache.put(userId, member.getEffectiveName());
                return member.getEffectiveName();
            } catch (RuntimeException ignored) {
                // Not a member of this guild, or not a valid id.
            }
        }
        return "Unknown User";
    }

    public String getRoleName(String roleId) {
        return roleCache.getOrDefault(roleId, "Unknown Role");
    }

    // Bulk lookup methods for efficiency
    public Map<String, String> getNicknames(java.util.List<String> userIds) {
        Map<String, String> result = new ConcurrentHashMap<>();
        for (String userId : userIds) {
            result.put(userId, getNickname(userId));
        }
        return result;
    }

    public Map<String, String> getRoleNames(java.util.List<String> roleIds) {
        Map<String, String> result = new ConcurrentHashMap<>();
        for (String roleId : roleIds) {
            result.put(roleId, getRoleName(roleId));
        }
        return result;
    }
}