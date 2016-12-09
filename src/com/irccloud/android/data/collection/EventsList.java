/*
 * Copyright (c) 2015 IRCCloud, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.irccloud.android.data.collection;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.text.TextUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.irccloud.android.ColorScheme;
import com.irccloud.android.IRCCloudJSONObject;
import com.irccloud.android.R;
import com.irccloud.android.data.model.Event;
import com.irccloud.android.fragment.MessageViewFragment;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.runtime.TransactionManager;
import com.raizlabs.android.dbflow.runtime.transaction.DeleteTransaction;
import com.raizlabs.android.dbflow.runtime.transaction.process.DeleteModelListTransaction;
import com.raizlabs.android.dbflow.runtime.transaction.process.ProcessModelInfo;
import com.raizlabs.android.dbflow.sql.builder.Condition;
import com.raizlabs.android.dbflow.sql.language.Delete;
import com.raizlabs.android.dbflow.sql.language.Select;
import com.raizlabs.android.dbflow.structure.ModelAdapter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;

@SuppressLint("UseSparseArrays")
public class EventsList {
    private final HashMap<Integer, TreeMap<Long, Event>> events;
    private HashSet<Integer> loaded_bids = new HashSet<>();
    private static EventsList instance = null;
    private ColorScheme colorScheme = ColorScheme.getInstance();

    public synchronized static EventsList getInstance() {
        if (instance == null)
            instance = new EventsList();
        return instance;
    }

    public EventsList() {
        events = new HashMap<>(100);
    }

    public void load(int bid) {
        /*synchronized (events) {
            Cursor c = null;
            try {
                if(loaded_bids.contains(bid))
                    return;
                long start = System.currentTimeMillis();
                ModelAdapter<Event> modelAdapter = FlowManager.getModelAdapter(Event.class);
                if (events.containsKey(bid) && events.get(bid) != null && events.get(bid).size() > 0) {
                    c = new Select().from(Event.class).where(Condition.column(Event$Table.BID).is(bid)).and(Condition.column(Event$Table.EID).lessThan(events.get(bid).firstKey())).query();
                } else {
                    c = new Select().from(Event.class).where(Condition.column(Event$Table.BID).is(bid)).query();
                }

                if (c != null && c.moveToFirst()) {
                    android.util.Log.d("IRCCloud", "Loading events for bid" + bid);
                    do {
                        addEvent(modelAdapter.loadFromCursor(c));
                    } while (c.moveToNext());
                    long time = System.currentTimeMillis() - start;
                    android.util.Log.i("IRCCloud", "Loaded " + c.getCount() + " events in " + time + "ms");
                    loaded_bids.add(bid);
                }
            } catch (SQLiteException e) {
                NetworkConnection.printStackTraceToCrashlytics(e);
            } finally {
                if(c != null)
                    c.close();
            }
        }*/
    }

    public void save() {
        /*synchronized (events) {
            for (int bid : events.keySet()) {
                TreeMap<Long, Event> e = events.get(bid);
                if (e != null) {
                    TransactionManager.getInstance().saveOnSaveQueue(e.values());
                }
            }
        }*/
    }

    public void clear() {
        synchronized (events) {
            events.clear();
            loaded_bids.clear();
            //Delete.table(Event.class);
        }
    }

    public void addEvent(Event event) {
        synchronized (events) {
            if (!events.containsKey(event.bid) || events.get(event.bid) == null)
                events.put(event.bid, new TreeMap<Long, Event>());

            events.get(event.bid).put(event.eid, event);
        }
    }

    public interface Formatter {
        void format(IRCCloudJSONObject event, Event e);
    }

    private HashMap<String, Formatter> formatterMap = new HashMap<String, Formatter>() {{
        put("socket_closed", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                e.from = "";
                e.row_type = MessageViewFragment.ROW_SOCKETCLOSED;
                e.color = colorScheme.timestampColor;
                e.linkify = false;
                if(event != null) {
                    if (event.has("pool_lost"))
                        e.msg = "Connection pool lost";
                    else if (event.has("server_ping_timeout"))
                        e.msg = "Server PING timed out";
                    else if (event.has("reason") && event.getString("reason").length() > 0)
                        e.msg = "Connection lost: " + reason(event.getString("reason"));
                    else if (event.has("abnormal"))
                        e.msg = "Connection closed unexpectedly";
                    else
                        e.msg = "";
                }
            }
        });

        put("user_channel_mode", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.target_mode = event.getString("newmode");
                    e.chan = event.getString("channel");
                }
            }
        });

        put("buffer_msg", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.target_mode = event.getString("statusmsg");
                }
            }
        });

        put("buffer_me_msg", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.nick = e.from;
                    e.from = "";
                }
            }
        });

        put("nickname_in_use", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = event.getString("nick");
                }
                e.msg = "is already in use";
                e.color = colorScheme.networkErrorColor;
                e.bg_color = colorScheme.errorBackgroundColor;
            }
        });

        put("unhandled_line", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = "";
                    StringBuilder msg = new StringBuilder();
                    if (event.has("command"))
                        msg.append(event.getString("command")).append(" ");
                    if (event.has("raw"))
                        msg.append(event.getString("raw"));
                    else
                        msg.append(event.getString("msg"));
                    e.msg = msg.toString();
                }
                e.color = colorScheme.networkErrorColor;
                e.bg_color = colorScheme.errorBackgroundColor;
            }
        });
        put("unparsed_line", get("unhandled_line"));

        put("connecting_cancelled", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                e.from = "";
                e.msg = "Cancelled";
                e.color = colorScheme.networkErrorColor;
                e.bg_color = colorScheme.errorBackgroundColor;
            }
        });

        put("connecting_failed", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                e.row_type = MessageViewFragment.ROW_SOCKETCLOSED;
                e.color = colorScheme.timestampColor;
                e.from = "";
                e.linkify = false;
                if(event != null) {
                    String reason = reason(event.getString("reason"));
                    if (reason != null) {
                        e.msg = "Failed to connect: " + reason;
                    } else {
                        e.msg = "Failed to connect.";
                    }
                }
            }
        });

        put("quit_server", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                e.from = "";
                e.msg = "⇐ You disconnected";
                e.color = colorScheme.timestampColor;
                e.self = false;
            }
        });

        put("self_details", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                e.bg_color = colorScheme.statusBackgroundColor;
                e.linkify = false;
                if(event != null) {
                    e.from = "";
                    if(event.has("usermask") && event.has("user") && event.getString("user").length() > 0) {
                        e.msg = "<pre>Your hostmask: <b>" + event.getString("usermask") + "</b></pre>";
                        if(event.has("server_realname")) {
                            Event e1 = new Event(e);
                            e1.eid++;
                            e1.msg = "<pre>Your name: <b>" + event.getString("server_realname") + "</b></pre>";
                            e1.linkify = true;
                            addEvent(e1);
                        }
                    } else if(event.has("server_realname")) {
                        e.msg = "<pre>Your name: <b>" + event.getString("server_realname") + "</b></pre>";
                        e.linkify = true;
                    }
                }
            }
        });

        put("myinfo", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = "";
                    StringBuilder msg = new StringBuilder();
                    msg.append("Host: ").append(event.getString("server")).append("\n");
                    msg.append("IRCd: ").append(event.getString("version")).append("\n");
                    msg.append("User modes: ").append(event.getString("user_modes")).append("\n");
                    msg.append("Channel modes: ").append(event.getString("channel_modes")).append("\n");
                    if (event.has("rest") && event.getString("rest").length() > 0)
                        msg.append("Parametric channel modes: ").append(event.getString("rest")).append("\n");
                    e.msg = "<pre>" + TextUtils.htmlEncode(msg.toString()) + "</pre>";
                }
                e.bg_color = colorScheme.statusBackgroundColor;
                e.linkify = false;
            }
        });

        put("user_mode", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = "";
                    e.msg = "<pre>Your user mode is: <b>+" + event.getString("newmode") + "</b></pre>";
                }
                e.bg_color = colorScheme.statusBackgroundColor;
            }
        });

        put("your_unique_id", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = "";
                    e.msg = "<pre>Your unique ID is: <b>" + event.getString("unique_id") + "</b></pre>";
                }
                e.bg_color = colorScheme.statusBackgroundColor;
            }
        });

        put("kill", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = "";
                    e.msg = "You were killed";
                    if (event.has("from"))
                        e.msg += " by " + event.getString("from");
                    if (event.has("killer_hostmask"))
                        e.msg += " (" + event.getString("killer_hostmask") + ")";
                    if (event.has("reason"))
                        e.msg += ": " + TextUtils.htmlEncode(event.getString("reason"));
                }
                e.bg_color = colorScheme.statusBackgroundColor;
                e.linkify = false;
            }
        });

        put("banned", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = "";
                    e.msg = "You were banned";
                    if (event.has("server"))
                        e.msg += " from " + event.getString("server");
                    if (event.has("reason"))
                        e.msg += ": " + TextUtils.htmlEncode(event.getString("reason"));
                }
                e.bg_color = colorScheme.statusBackgroundColor;
                e.linkify = false;
            }
        });

        put("channel_topic", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    if (event.has("author"))
                        e.from = event.getString("author");
                    else
                        e.from = event.getString("server");
                    if (event.getString("topic") != null && event.getString("topic").length() > 0)
                        e.msg = "set the topic: " + TextUtils.htmlEncode(event.getString("topic"));
                    else
                        e.msg = "cleared the topic";
                }
                e.bg_color = colorScheme.statusBackgroundColor;
            }
        });

        put("channel_mode", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.nick = e.from;
                    e.from = "";
                    e.msg = "Channel mode set to: <b>" + event.getString("diff") + "</b>";
                }
                e.bg_color = colorScheme.statusBackgroundColor;
                e.linkify = false;
                e.self = false;
            }
        });

        put("channel_mode_is", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = "";
                    if (event.getString("diff") != null && event.getString("diff").length() > 0)
                        e.msg = "Channel mode is: <b>" + event.getString("diff") + "</b>";
                    else
                        e.msg = "No channel mode";
                }
                e.bg_color = colorScheme.statusBackgroundColor;
                e.linkify = false;
            }
        });

        put("kicked_channel", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = "";
                    e.from_mode = event.getString("kicker_mode");
                    e.from_hostmask = event.getString("kicker_hostmask");
                    e.old_nick = event.getString("nick");
                    e.nick = event.getString("kicker");
                }
                e.color = colorScheme.timestampColor;
                e.linkify = false;
                if (e.self)
                    e.row_type = MessageViewFragment.ROW_SOCKETCLOSED;
            }
        });
        put("you_kicked_channel", get("kicked_channel"));

        put("channel_mode_list_change", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    boolean unknown = true;
                    JsonNode ops = event.getJsonObject("ops");
                    if (ops != null) {
                        JsonNode add = ops.get("add");
                        if (add != null && add.size() > 0) {
                            JsonNode op = add.get(0);
                            if (op.get("mode").asText().equals("b")) {
                                e.nick = e.from;
                                e.from = "";
                                e.msg = "banned <b>" + op.get("param").asText() + "</b> (<font color=#808080>+b</font>)";
                                unknown = false;
                            } else if (op.get("mode").asText().equals("e")) {
                                e.nick = e.from;
                                e.from = "";
                                e.msg = "exempted <b>" + op.get("param").asText() + "</b> from bans (<font color=#808080>+e</font>)";
                                unknown = false;
                            } else if (op.get("mode").asText().equals("q")) {
                                if (op.get("param").asText().contains("@") || op.get("param").asText().contains("$")) {
                                    e.nick = e.from;
                                    e.from = "";
                                    e.msg = "quieted <b>" + op.get("param").asText() + "</b> (<font color=#808080>+q</font>)";
                                } else {
                                    e.type = "user_channel_mode";
                                    e.chan = event.getString("channel");
                                    e.nick = op.get("param").asText();
                                }
                                unknown = false;
                            } else if (op.get("mode").asText().equals("I")) {
                                e.nick = e.from;
                                e.from = "";
                                e.msg = "added <b>" + op.get("param").asText() + "</b> to the invite list (<font color=#808080>+I</font>)";
                                unknown = false;
                            }
                        }
                        JsonNode remove = ops.get("remove");
                        if (remove != null && remove.size() > 0) {
                            JsonNode op = remove.get(0);
                            if (op.get("mode").asText().equals("b")) {
                                e.nick = e.from;
                                e.from = "";
                                e.msg = "un-banned <b>" + op.get("param").asText() + "</b> (<font color=#808080>-b</font>)";
                                unknown = false;
                            } else if (op.get("mode").asText().equals("e")) {
                                e.nick = e.from;
                                e.from = "";
                                e.msg = "un-exempted <b>" + op.get("param").asText() + "</b> from bans (<font color=#808080>-e</font>)";
                                unknown = false;
                            } else if (op.get("mode").asText().equals("q")) {
                                if (op.get("param").asText().contains("@") || op.get("param").asText().contains("$")) {
                                    e.nick = e.from;
                                    e.from = "";
                                    e.msg = "un-quieted <b>" + op.get("param").asText() + "</b> (<font color=#808080>-q</font>)";
                                } else {
                                    e.type = "user_channel_mode";
                                    e.chan = event.getString("channel");
                                    e.nick = op.get("param").asText();
                                }
                                unknown = false;
                            } else if (op.get("mode").asText().equals("I")) {
                                e.nick = e.from;
                                e.from = "";
                                e.msg = "removed <b>" + op.get("param").asText() + "</b> from the invite list (<font color=#808080>-I</font>)";
                                unknown = false;
                            }
                        }
                    }
                    if (unknown) {
                        e.nick = e.from;
                        e.from = "";
                        e.msg = "set channel mode: <b>" + event.getString("diff") + "</b>";
                    }
                }
                e.bg_color = colorScheme.statusBackgroundColor;
                e.linkify = false;
                e.self = false;
            }
        });

        put("motd_response", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    JsonNode lines = event.getJsonNode("lines");
                    e.from = "";
                    if (lines != null) {
                        StringBuilder builder = new StringBuilder("<pre>");
                        if (event.has("start"))
                            builder.append(event.getString("start")).append("<br/>");
                        for (int i = 0; i < lines.size(); i++) {
                            builder.append(TextUtils.htmlEncode(lines.get(i).asText()).replace("  ", " &nbsp;")).append("<br/>");
                        }
                        builder.append("</pre>");
                        e.msg = builder.toString();
                    }
                }
                e.bg_color = colorScheme.selfBackgroundColor;
            }
        });
        put("server_motd", get("motd_response"));
        put("info_response", get("motd_response"));

        put("notice", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.chan = event.getString("target");
                    e.nick = event.getString("target");
                    e.target_mode = event.getString("statusmsg");
                    e.msg = "<pre>" + e.msg + "</pre>";
                }
                e.bg_color = colorScheme.noticeBackgroundColor;
            }
        });

        put("newsflash", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.msg = "<pre>" + e.msg + "</pre>";
                }
                e.bg_color = colorScheme.noticeBackgroundColor;
            }
        });

        put("invited", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = event.getString("inviter");
                    e.msg = "<pre>invited " + event.getString("invitee") + " to join " + event.getString("channel") + "</pre>";
                }
                e.bg_color = colorScheme.noticeBackgroundColor;
            }
        });

        put("generic_server_info", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.msg = "<pre>" + e.msg + "</pre>";
                }
                e.bg_color = colorScheme.noticeBackgroundColor;
            }
        });

        put("rehashed_config", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.msg = "Rehashed config: " + event.getString("file") + "(" + e.msg + ")";
                    e.msg = "<pre>" + e.msg + "</pre>";
                }
                e.bg_color = colorScheme.noticeBackgroundColor;
            }
        });

        put("knock", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    if (e.nick != null && e.nick.length() > 0) {
                        e.from = e.nick;
                        if (e.hostmask != null && e.hostmask.length() > 0)
                            e.msg += " (" + e.hostmask + ")";
                    } else {
                        e.msg = event.getString("userhost") + " " + e.msg;
                    }
                    e.msg = "<pre>" + e.msg + "</pre>";
                }
                e.bg_color = colorScheme.noticeBackgroundColor;
            }
        });

        put("hidden_host_set", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                e.bg_color = colorScheme.statusBackgroundColor;
                e.linkify = false;
                if(event != null) {
                    e.from = "";
                    e.msg = "<b>" + event.getString("hidden_host") + "</b> " + e.msg;
                }
            }
        });

        put("inviting_to_channel", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = "";
                    e.msg = "<pre>You invited " + event.getString("recipient") + " to join " + event.getString("channel") + "</pre>";
                }
                e.bg_color = colorScheme.noticeBackgroundColor;
            }
        });

        put("channel_invite", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.msg = "<pre>Invite to join " + event.getString("channel") + "</pre>";
                    e.old_nick = event.getString("channel");
                }
                e.bg_color = colorScheme.noticeBackgroundColor;
            }
        });

        put("callerid", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = e.nick;
                    e.msg = "<pre>" + e.msg + "</pre>";
                    e.highlight = true;
                    e.linkify = false;
                    e.hostmask = event.getString("usermask");
                }
            }
        });

        put("target_callerid", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = event.getString("target_nick");
                    e.msg = "<pre>" + e.msg + "</pre>";
                }
                e.color = colorScheme.networkErrorColor;
                e.bg_color = colorScheme.errorBackgroundColor;
            }
        });

        put("target_notified", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = event.getString("target_nick");
                    e.msg = "<pre>" + e.msg + "</pre>";
                }
                e.color = colorScheme.networkErrorColor;
                e.bg_color = colorScheme.errorBackgroundColor;
            }
        });

        put("link_channel", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    if (event.has("invalid_chan")) {
                        if (event.has("valid_chan")) {
                            e.msg = event.getString("invalid_chan") + " → " + event.getString("valid_chan") + " " + e.msg;
                        } else {
                            e.msg = event.getString("invalid_chan") + " " + e.msg;
                        }
                    }
                }
                e.color = colorScheme.networkErrorColor;
                e.bg_color = colorScheme.errorBackgroundColor;
            }
        });

        String[] statuses = {
                "server_motdstart",
                "server_welcome",
                "server_motd",
                "server_endofmotd",
                "server_nomotd",
                "server_luserclient",
                "server_luserop",
                "server_luserconns",
                "server_luserme",
                "server_n_local",
                "server_luserchannels",
                "server_n_global",
                "server_yourhost",
                "server_created",
                "server_luserunknown",
                "server_snomask",
                "starircd_welcome",
                "zurna_motd",
                "wait",
                "logged_in_as",
                "btn_metadata_set",
                "sasl_success",
                "you_are_operator",
                "codepage",
                "logged_out",
                "nick_locked",
                "text",
                "admin_info"
        };
        Formatter statusFormatter = new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                e.bg_color = colorScheme.statusBackgroundColor;
                if(event != null) {
                    e.msg = "<pre>" + e.msg + "</pre>";
                    e.from = "";
                    if (!e.type.equals("server_motd") && !e.type.equals("zurna_motd"))
                        e.linkify = false;
                }
            }
        };
        for (String status : statuses)
            put(status, statusFormatter);

        String[] stats = {
                "stats", "statslinkinfo", "statscommands", "statscline", "statsnline", "statsiline", "statskline", "statsqline", "statsyline", "statsbline", "statsgline", "statstline", "statseline", "statsvline", "statslline", "statsuptime", "statsoline", "statshline", "statssline", "statsuline", "statsdebug", "spamfilter", "endofstats"
        };
        Formatter statsFormatter = new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = "";
                    if (event.has("parts") && event.getString("parts").length() > 0)
                        e.msg = event.getString("parts") + ": " + e.msg;
                    e.msg = "<pre>" + e.msg + "</pre>";
                }
                e.bg_color = colorScheme.statusBackgroundColor;
                e.linkify = false;
            }
        };
        for (String stat : stats)
            put(stat, statsFormatter);

        String[] caps = {
                "cap_ls", "cap_req", "cap_ack", "cap_raw", "cap_new"
        };
        Formatter capsFormatter = new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = "";
                    e.linkify = false;
                    switch (e.type) {
                        case "cap_ls":
                            e.msg = "<b>CAP</b> Server supports: ";
                            break;
                        case "cap_req":
                            e.msg = "<b>CAP</b> Requesting: ";
                            break;
                        case "cap_ack":
                            e.msg = "<b>CAP</b> Acknowledged: ";
                            break;
                        case "cap_nak":
                            e.msg = "<b>CAP</b> Rejected: ";
                            break;
                        case "cap_raw":
                            e.msg = "<b>CAP</b> " + event.getString("line");
                            break;
                        case "cap_new":
                            e.msg = "<b>CAP</b> Server added: ";
                            break;
                        case "cap_del":
                            e.msg = "<b>CAP</b> Server removed: ";
                            break;
                    }
                    JsonNode caps = event.getJsonNode("caps");
                    if (caps != null) {
                        for (int i = 0; i < caps.size(); i++) {
                            if (i > 0)
                                e.msg += " | ";
                            e.msg += caps.get(i).asText();
                        }
                    }
                    e.msg = "<pre>" + e.msg + "</pre>";
                }
                e.bg_color = colorScheme.statusBackgroundColor;
            }
        };
        for (String cap : caps)
            put(cap, capsFormatter);

        String[] helps = {
                "help_topics_start", "help_topics", "help_topics_end", "helphdr", "helpop", "helptlr", "helphlp", "helpfwd", "helpign"
        };
        Formatter helpsFormatter = new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                e.bg_color = colorScheme.statusBackgroundColor;
                if(event != null) {
                    e.msg = "<pre>" + e.msg + "</pre>";
                    e.from = "";
                }
            }
        };
        for (String help : helps)
            put(help, helpsFormatter);

        String[] errors = {
                "too_fast", "sasl_fail", "sasl_too_long", "sasl_aborted", "sasl_already", "no_bots", "msg_services", "bad_ping", "not_for_halfops", "ambiguous_error_message", "list_syntax", "who_syntax"
        };
        Formatter errorFormatter = new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = "";
                }
                e.color = colorScheme.networkErrorColor;
                e.bg_color = colorScheme.errorBackgroundColor;
            }
        };
        for (String error : errors)
            put(error, errorFormatter);

        put("version", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = "";
                    e.msg = "<pre><b>" + event.getString("server_version") + "</b> " + event.getString("comments") + "</pre>";
                }
                e.bg_color = colorScheme.statusBackgroundColor;
                e.linkify = false;
            }
        });

        put("services_down", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = event.getString("services_name");
                }
                e.color = colorScheme.networkErrorColor;
                e.bg_color = colorScheme.errorBackgroundColor;
            }
        });

        put("unknown_umode", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = "";
                    if (event.has("flag"))
                        e.msg = "<b>" + event.getString("flag") + "</b> " + e.msg;
                }
                e.color = colorScheme.networkErrorColor;
                e.bg_color = colorScheme.errorBackgroundColor;
            }
        });

        put("kill_deny", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = event.getString("channel");
                }
                e.color = colorScheme.networkErrorColor;
                e.bg_color = colorScheme.errorBackgroundColor;
            }
        });

        put("chan_own_priv_needed", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = event.getString("channel");
                }
                e.color = colorScheme.networkErrorColor;
                e.bg_color = colorScheme.errorBackgroundColor;
            }
        });

        put("chan_forbidden", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.from = event.getString("channel");
                }
                e.color = colorScheme.networkErrorColor;
                e.bg_color = colorScheme.errorBackgroundColor;
            }
        });

        put("list_usage", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                if(event != null) {
                    e.msg = "<pre>" + e.msg + "</pre>";
                }
                e.bg_color = colorScheme.noticeBackgroundColor;
            }
        });

        put("time", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                e.bg_color = colorScheme.statusBackgroundColor;
                if(event != null) {
                    e.msg = "<pre>" + event.getString("time_string");
                    if (event.has("time_stamp") && event.getString("time_stamp").length() > 0)
                        e.msg += " (" + event.getString("time_stamp") + ")";
                    e.msg += " — <b>" + event.getString("time_server") + "</b></pre>";
                    e.linkify = false;
                }
            }
        });

        put("watch_status", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                e.bg_color = colorScheme.statusBackgroundColor;
                if(event != null) {
                    e.from = event.getString("watch_nick");
                    e.msg = "<pre>" + e.msg + " (" + event.getString("username") + "@" + event.getString("userhost") + ")</pre>";
                    e.linkify = false;
                }
            }
        });

        put("sqline_nick", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                e.bg_color = colorScheme.statusBackgroundColor;
                if(event != null) {
                    e.from = event.getString("charset");
                    e.msg = "<pre>" + e.msg + "</pre>";
                }
            }
        });

        put("error", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                e.color = colorScheme.networkErrorColor;
                e.bg_color = colorScheme.errorBackgroundColor;
            }
        });

        put("you_parted_channel", new Formatter() {
            @Override
            public void format(IRCCloudJSONObject event, Event e) {
                e.row_type = MessageViewFragment.ROW_SOCKETCLOSED;
            }
        });

    }};

    private String reason(String reason) {
        if (reason != null) {
            if (reason.equalsIgnoreCase("pool_lost")) {
                return "Connection pool failed";
            } else if (reason.equalsIgnoreCase("no_pool")) {
                return "No available connection pools";
            } else if (reason.equalsIgnoreCase("enetdown")) {
                return "Network down";
            } else if (reason.equalsIgnoreCase("etimedout") || reason.equalsIgnoreCase("timeout")) {
                return "Timed out";
            } else if (reason.equalsIgnoreCase("ehostunreach")) {
                return "Host unreachable";
            } else if (reason.equalsIgnoreCase("econnrefused")) {
                return "Connection refused";
            } else if (reason.equalsIgnoreCase("nxdomain")) {
                return "Invalid hostname";
            } else if (reason.equalsIgnoreCase("server_ping_timeout")) {
                return "PING timeout";
            } else if (reason.equalsIgnoreCase("ssl_certificate_error")) {
                return "SSL certificate error";
            } else if (reason.equalsIgnoreCase("ssl_error")) {
                return "SSL error";
            } else if (reason.equalsIgnoreCase("crash")) {
                return "Connection crashed";
            }
        }
        return reason;
    }

    public Event addEvent(IRCCloudJSONObject event) {
        synchronized (events) {
            if (!events.containsKey(event.bid()))
                events.put(event.bid(), new TreeMap<Long, Event>());

            Event e = getEvent(event.eid(), event.bid());
            if (e == null) {
                e = new Event();
                events.get(event.bid()).put(event.eid(), e);
            }
            e.cid = event.cid();
            e.bid = event.bid();
            e.eid = event.eid();
            e.type = event.type();
            e.color = colorScheme.messageTextColor;
            e.bg_color = colorScheme.contentBackgroundColor;
            e.msg = event.getString("msg");
            e.hostmask = event.getString("hostmask");
            e.from = event.getString("from");
            e.from_mode = event.getString("from_mode");
            e.from_realname = event.getString("from_realname");
            if(e.from_realname != null && e.from_realname.length() > 0 && e.from_realname.equals(e.from))
                e.from_realname = null;
            if(event.has("server_time"))
                e.server_time = event.getLong("server_time");
            else
                e.server_time = 0;

            e.chan = event.getString("chan");
            if (event.has("newnick"))
                e.nick = event.getString("newnick");
            else if (event.has("nick"))
                e.nick = event.getString("nick");
            else
                e.nick = null;
            e.old_nick = event.getString("oldnick");
            e.server = event.getString("server");
            e.diff = event.getString("diff");
            e.highlight = event.getBoolean("highlight");
            e.self = event.getBoolean("self");
            e.to_chan = event.getBoolean("to_chan");
            e.to_buffer = event.getBoolean("to_buffer");
            e.ops = event.getJsonNode("ops");
            e.entities = event.getJsonNode("entities");

            if (event.has("reqid"))
                e.reqid = event.getInt("reqid");
            else
                e.reqid = -1;

            if (e.from != null)
                e.from = TextUtils.htmlEncode(e.from);

            if (e.msg != null) {
                e.msg = TextUtils.htmlEncode(e.msg).replace("  ", "&nbsp; ");
                if(e.msg.startsWith(" "))
                    e.msg = "&nbsp;" + e.msg.substring(1);
            }

            Formatter f = formatterMap.get(e.type);
            if (f != null)
                f.format(event, e);

            if (event.has("value") && !event.type().startsWith("cap_")) {
                e.msg = "<pre>" + event.getString("value") + " " + e.msg + "</pre>";
            }

            if (e.highlight)
                e.bg_color = colorScheme.highlightBackgroundColor;

            if (e.self && !e.type.equals("notice"))
                e.bg_color = colorScheme.selfBackgroundColor;

            return e;
        }
    }

    public int getSizeOfBuffer(int bid) {
        synchronized (events) {
            load(bid);
            if (events.containsKey(bid) && events.get(bid) != null)
                return events.get(bid).size();
        }
        return 0;
    }

    public Event getEvent(long eid, int bid) {
        synchronized (events) {
            if (events.containsKey(bid) && events.get(bid) != null)
                return events.get(bid).get(eid);
        }
        return null;
    }

    public void deleteEvent(long eid, int bid) {
        synchronized (events) {
            if (events.containsKey(bid) && events.get(bid) != null && events.get(bid).containsKey(eid))
                events.get(bid).remove(eid);
        }
        //new Delete().from(Event.class).where(Condition.column(Event$Table.BID).is(bid)).and(Condition.column(Event$Table.EID).is(eid)).queryClose();
    }

    public void deleteEventsForBuffer(int bid) {
        synchronized (events) {
            if (events.containsKey(bid) && events.get(bid) != null)
                events.remove(bid);
        }
        //new Delete().from(Event.class).where(Condition.column(Event$Table.BID).is(bid)).queryClose();
    }

    public TreeMap<Long, Event> getEventsForBuffer(int bid) {
        synchronized (events) {
            load(bid);
            if (events.containsKey(bid) && events.get(bid) != null) {
                return events.get(bid);
            }
        }
        return null;
    }

    public Long lastEidForBuffer(int bid) {
        synchronized (events) {
            if (events.containsKey(bid) && events.get(bid) != null && events.get(bid).size() > 0) {
                Long[] eids = events.get(bid).keySet().toArray(new Long[events.get(bid).keySet().size()]);
                if (eids.length > 0)
                    return eids[eids.length - 1];
            /*} else {
                Event e = new Select().from(Event.class).where(Condition.column(Event$Table.BID).is(bid)).orderBy(true, Event$Table.EID).limit(1).querySingle();
                if(e != null) {
                    return e.eid;
                }*/
            }
        }
        return 0L;
    }

    public void pruneEvents(int bid) {
        synchronized (events) {
            load(bid);
            TreeMap<Long, Event> e = events.get(bid);
            while (e != null && e.size() > 50 && e.firstKey() != null) {
                e.remove(e.firstKey());
            }
            /*if(e != null)
                new Delete().from(Event.class).where(Condition.column(Event$Table.BID).is(bid)).and(Condition.column(Event$Table.EID).lessThan(e.firstKey())).queryClose();*/
        }
    }

    public synchronized void clearCaches() {
        synchronized (events) {
            for (int bid : events.keySet()) {
                if (events.containsKey(bid) && events.get(bid) != null) {
                    for (Event e : events.get(bid).values()) {
                        e.color = colorScheme.messageTextColor;
                        e.bg_color = colorScheme.contentBackgroundColor;
                        e.timestamp = null;
                        e.html = null;
                        e.formatted = null;
                        e.formatted_nick = null;
                        e.formatted_realname = null;
                        Formatter f = formatterMap.get(e.type);
                        if (f != null)
                            f.format(null, e);

                        if (e.highlight)
                            e.bg_color = colorScheme.highlightBackgroundColor;

                        if (e.self)
                            e.bg_color = colorScheme.selfBackgroundColor;
                    }
                }
            }
        }
    }

    public synchronized Event findPendingEventForReqid(int bid, int reqid) {
        synchronized (events) {
            load(bid);
            for (Event e : events.get(bid).values()) {
                if(e.reqid == reqid && (e.pending || e.failed))
                    return e;
            }
        }
        return null;
    }


    public synchronized void clearPendingEvents(int bid) {
        Event[] i;
        synchronized (events) {
            i = events.get(bid).values().toArray(new Event[events.get(bid).values().size()]);
        }
        for (Event e : i) {
            if(e.pending || e.failed) {
                if(e.expiration_timer != null) {
                    try {
                        e.expiration_timer.cancel();
                    } catch (Exception e1) {
                        //Timer already cancelled
                    }
                    e.expiration_timer = null;
                }
                deleteEvent(e.eid, e.bid);
            }
        }
    }
}
