/**
 *  Feirebjelle
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Ring i bjella når noe skal feires: lysene går i diskofarger, høyttaleren
 *  roper ut en hyllest og alle får et varsel på telefonen. Etterpå går lysene
 *  tilbake slik de var.
 */
definition(
    name: "Feirebjelle",
    namespace: "andrebir",
    author: "Axon prosjekt AS",
	description: "Ring i bjella for å feire: disko i lysene, hyllest på høyttaleren og varsel til alle.",
	category: "Fun & Social",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/light_outlet.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/light_outlet@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Meta/light_outlet@2x.png")

preferences
{
	page(name: "getPref")
}

def getPref()
{
    dynamicPage(name: "getPref", title: "Sett opp Feirebjelle", install: true, uninstall: true)
    {
        section("Hva ringer i bjella?")
        {
            input(name: "buttons", type: "capability.button", title: "Når denne knappen trykkes", required: false, multiple: true)
            input(name: "triggerSwitch", type: "capability.switch", title: "Eller når denne bryteren slås på (f.eks. en virtuell «Feire»-bryter)", required: false, multiple: false)
        }
        section("Disko")
        {
            input(name: "colorLights", type: "capability.colorControl", title: "Fargelys", required: false, multiple: true)
            input(name: "rounds", type: "number", title: "Antall fargeskift", required: true, defaultValue: 12, range: "1..60")
        }
        section("Lyd og varsel")
        {
            input(name: "speakers", type: "capability.speechSynthesis", title: "Høyttaler som roper ut", required: false, multiple: true)
            input(name: "reason", type: "string", title: "Hva feirer vi? (valgfritt)", required: false, description: "f.eks. «ferdigstillelse av byggetrinn 2»")
            input(name: "sendNotification", type: "bool", title: "Send varsel til telefonen", required: true, defaultValue: true)
        }
    }
}

def installed()
{
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated()
{
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	unschedule()
	initialize()
}

def initialize()
{
	subscribe(buttons, "button.pushed", ringHandler)
	subscribe(triggerSwitch, "switch.on", ringHandler)
    atomicState.partying = false
}

def ringHandler(evt)
{
    if (atomicState.partying)
    {
        log.debug "Feiringen er allerede i gang"
        return
    }
    atomicState.partying = true
    log.debug "Bjella ringer! Utløst av ${evt.displayName}"

    def message = cheer()
    speakers*.speak(message)
    if (sendNotification)
    {
        sendPush("🔔 ${message}")
    }

    saveLightStates()
    atomicState.round = 0
    discoStep()
}

def cheer()
{
    def what = reason ? " for ${reason}" : ""
    def cheers = [
        "Ding ding ding! Gratulerer${what}! Dette fortjener kake.",
        "Hipp hipp hurra${what}! Godt jobbet, alle sammen!",
        "Ferdig er bedre enn perfekt, og dette ble begge deler${what}!",
        "Alle tekniske funksjoner er testet og godkjent: feiring${what} er satt i drift!",
        "Gratulerer${what}! Grensesnittet mellom jobb og fest er nå åpnet.",
        "Bjella har talt${what}. Nå er det lov å juble!"
    ]
    return cheers[new Random().nextInt(cheers.size())]
}

def saveLightStates()
{
    def states = [:]
    colorLights?.each
    {
        states[it.id] = [
            switch: it.currentValue("switch"),
            hue: it.currentValue("hue"),
            saturation: it.currentValue("saturation"),
            level: it.currentValue("level")
        ]
    }
    atomicState.lightStates = states
}

def discoStep()
{
    def round = atomicState.round as int
    def maxRounds = (rounds ?: 12) as int
    if (!colorLights || round >= maxRounds)
    {
        restoreLights()
        return
    }

    // Rød, oransje, gul, grønn, blå og lilla i hue 0..100
    def palette = [0, 8, 16, 33, 66, 83]
    def hue = palette[round % palette.size()]
    colorLights*.setColor([hue: hue, saturation: 100, level: 100])

    atomicState.round = round + 1
    runIn(1, discoStep)
}

def restoreLights()
{
    def states = atomicState.lightStates ?: [:]
    colorLights?.each
    {
        def s = states[it.id]
        if (s)
        {
            if (s.hue != null && s.saturation != null)
            {
                it.setColor([hue: s.hue, saturation: s.saturation, level: s.level ?: 100])
            }
            if (s.switch == "off")
            {
                it.off()
            }
        }
    }
    if (triggerSwitch?.currentValue("switch") == "on")
    {
        triggerSwitch.off()
    }
    atomicState.partying = false
    log.debug "Feiringen er over, lysene er tilbake"
}
