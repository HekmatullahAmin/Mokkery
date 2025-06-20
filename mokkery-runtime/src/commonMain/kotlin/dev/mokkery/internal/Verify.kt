@file:Suppress("unused")

package dev.mokkery.internal

import dev.mokkery.MokkerySuiteScope
import dev.mokkery.internal.calls.CallTrace
import dev.mokkery.internal.calls.callTracing
import dev.mokkery.internal.context.MocksRegistry
import dev.mokkery.internal.context.tools
import dev.mokkery.internal.names.GroupMockReceiverShortener
import dev.mokkery.internal.names.createGroupMockReceiverShortener
import dev.mokkery.internal.templating.TemplatingScope
import dev.mokkery.internal.utils.MocksCollection
import dev.mokkery.internal.utils.getScope
import dev.mokkery.internal.utils.orEmpty
import dev.mokkery.internal.utils.plus
import dev.mokkery.internal.utils.runSuspension
import dev.mokkery.verify.VerifyMode

internal fun MokkerySuiteScope.internalVerifySuspend(
    mode: VerifyMode,
    block: suspend TemplatingScope.() -> Unit
) = internalVerify(mode) { runSuspension { block() } }

internal fun MokkerySuiteScope.internalVerify(
    mode: VerifyMode,
    block: TemplatingScope.() -> Unit
) {
    val templating = TemplatingScope().apply(block)
    val mocks = mokkeryContext[MocksRegistry]?.mocks.orEmpty() + templating.mocks
    val calls = mocks
        .scopes
        .map { it.callTracing.unverified }
        .flatten()
        .sortedBy(CallTrace::orderStamp)
    val shortener = tools.createGroupMockReceiverShortener()
    shortener.prepare(calls, templating.templates)
    val verifier = tools.verifierFactory.create(mode, mocks.withShortener(shortener))
    verifier
        .verify(shortener.shortenTraces(calls), shortener.shortenTemplates(templating.templates))
        .map(shortener::getOriginalTrace)
        .forEach { mocks.getScope(it.mockId).callTracing.markVerified(it) }
}

internal fun MocksCollection.withShortener(shortener: GroupMockReceiverShortener): MocksCollection {
    val mocks = this
    return object : MocksCollection {
        override val ids: Set<MockId>
            get() = mocks.ids
        override val scopes: Collection<MokkeryInstanceScope>
            get() = mocks.scopes
        override fun getScopeOrNull(id: MockId): MokkeryInstanceScope? =
            mocks.getScopeOrNull(shortener.getOriginalId(id))
    }
}
