# Ask and Autonomous

[简体中文](modes.md) | English

When Mika works for you, the mode decides which steps it checks with you first. There is one global switch: "Developer options" (开发者选项) → "Autonomous mode" (自主模式) in Settings. Off means Ask, on means Autonomous.

| Mode | Behavior |
|---|---|
| **Ask** (default) | Only the step that spends money is confirmed with you first: placing or submitting an order, or a confirm/submit while the page shows an amount due. The card says where, what and how much, and you can approve just this once or decline. The payment itself is done by you. Sending messages, posting, submitting forms, deleting and changing settings are not asked about; they are just done. |
| **Autonomous** | Does not ask even before paying. The spacing between items in batch jobs still applies. |

- The switch applies to all tasks from then on; if a running task switches from Ask to Autonomous, payments are no longer asked about from the next step.
- In both modes Mika asks you to take over for things only you should do, like logging in, verification codes and payment passwords. It also asks when information is missing or only you can decide (e.g. which of two contacts with the same name).
- For tasks that run while you are away, such as scheduled tasks, Ask mode does not wait at a money-spending step: it skips it and tells you plainly which step was not done. Everything else is completed as usual.
- On a rooted phone with “Allow Mika to run root commands” turned on (off by default, see the [setup guide](setup-guide.en.md)): in Ask mode **every root command, including a read-only one,** first shows you the exact command and approves only that one. Unattended tasks, non-UI sources, child tasks and condition checks do not run root commands; only an explicitly autonomous, attended UI task may run without a prompt. An obvious su invocation in the regular shell is pointed to the dedicated root tool, but that is not a complete security sandbox.

You are responsible for actions you approve, and for actions Mika takes on your behalf in Autonomous mode. For anything important, use Ask mode and check the results; see the [disclaimer](disclaimer.en.md).
