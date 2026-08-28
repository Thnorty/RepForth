// Sample content for the phone kit. Every user-facing string exists in EN and TR.
const STR = {
  en: { today:"Today", plans:"Plans", catalog:"Exercises", progress:"Progress", week:"Week 4 · Push / Pull / Legs",
    start:"Start workout", resume:"Resume session", newWorkout:"New workout", generate:"Generate with AI",
    exercises:"exercises", min:"min", sets:"Sets", weight:"Weight", reps:"Reps", rest:"Rest", skipRest:"Skip rest",
    logSet:"Log set", finish:"Finish workout", search:"Search 1,324 exercises", filters:"Filters",
    muscle:"Muscle", equipment:"Equipment", howTo:"How to", history:"History", records:"Records",
    settingsTitle:"Settings", theme:"Theme", language:"Language", units:"Units", keepAwake:"Keep screen on",
    keepAwakeSub:"While a workout is running", watch:"Wear OS companion", watchSub:"Acts as a remote for this phone",
    local:"Logged sets stay on this phone. No account, no upload.", nextUp:"Next up", done:"Done", set:"Set",
    lastTime:"Last time", discard:"Discard this workout?", discardBody:"Logged sets stay on this phone. Nothing is uploaded.",
    keepGoing:"Keep going", discardYes:"Discard", setLogged:"Set logged", undo:"Undo", builder:"Build workout",
    addExercise:"Add exercise", save:"Save workout", volume:"Volume", prs:"PRs this month", streak:"Week streak" },
  tr: { today:"Bugün", plans:"Programlar", catalog:"Egzersizler", progress:"Gelişim", week:"Hafta 4 · İt / Çek / Bacak",
    start:"Antrenmanı başlat", resume:"Antrenmana dön", newWorkout:"Yeni antrenman", generate:"Yapay zeka ile oluştur",
    exercises:"egzersiz", min:"dk", sets:"Set", weight:"Ağırlık", reps:"Tekrar", rest:"Dinlenme", skipRest:"Dinlenmeyi atla",
    logSet:"Seti kaydet", finish:"Antrenmanı bitir", search:"1.324 egzersizde ara", filters:"Filtreler",
    muscle:"Kas grubu", equipment:"Ekipman", howTo:"Nasıl yapılır", history:"Geçmiş", records:"Rekorlar",
    settingsTitle:"Ayarlar", theme:"Tema", language:"Dil", units:"Birim", keepAwake:"Ekranı açık tut",
    keepAwakeSub:"Antrenman sürerken", watch:"Wear OS eşlikçisi", watchSub:"Telefondaki antrenmanın kumandası",
    local:"Kaydedilen setler yalnızca bu telefonda kalır. Hesap yok, yükleme yok.", nextUp:"Sırada", done:"Bitti", set:"Set",
    lastTime:"Geçen sefer", discard:"Bu antrenman silinsin mi?", discardBody:"Kaydedilen setler bu telefonda kalır. Hiçbir şey yüklenmez.",
    keepGoing:"Devam et", discardYes:"Sil", setLogged:"Set kaydedildi", undo:"Geri al", builder:"Antrenman oluştur",
    addExercise:"Egzersiz ekle", save:"Antrenmanı kaydet", volume:"Hacim", prs:"Bu ayın rekorları", streak:"Haftalık seri" }
};

const MUSCLE = { chest:{en:"Chest",tr:"Göğüs",icon:"accessibility_new"}, back:{en:"Back",tr:"Sırt",icon:"airline_seat_recline_normal"},
  shoulders:{en:"Shoulders",tr:"Omuz",icon:"sports_martial_arts"}, triceps:{en:"Triceps",tr:"Triceps",icon:"exercise"},
  biceps:{en:"Biceps",tr:"Biceps",icon:"sports_gymnastics"}, legs:{en:"Legs",tr:"Bacak",icon:"directions_run"},
  core:{en:"Core",tr:"Karın",icon:"self_improvement"} };
const EQUIP = { barbell:{en:"Barbell",tr:"Halter",icon:"fitness_center"}, dumbbell:{en:"Dumbbell",tr:"Dambıl",icon:"exercise"},
  cable:{en:"Cable",tr:"Kablo",icon:"cable"}, machine:{en:"Machine",tr:"Makine",icon:"precision_manufacturing"},
  bodyweight:{en:"Bodyweight",tr:"Vücut ağırlığı",icon:"accessibility"} };

const EXERCISES = [
  { id:"bench", en:"Barbell bench press", tr:"Bench press (halter)", muscle:"chest", equip:"barbell", sets:4, reps:8, weight:82.5, pr:true },
  { id:"incline", en:"Incline dumbbell press", tr:"Eğimli dambıl press", muscle:"chest", equip:"dumbbell", sets:3, reps:12, weight:28 },
  { id:"fly", en:"Cable chest fly", tr:"Kablo ile göğüs açma", muscle:"chest", equip:"cable", sets:3, reps:15, weight:15 },
  { id:"ohp", en:"Standing overhead press", tr:"Ayakta omuz press", muscle:"shoulders", equip:"barbell", sets:4, reps:6, weight:47.5 },
  { id:"lateral", en:"Dumbbell lateral raise", tr:"Yana dambıl kaldırma", muscle:"shoulders", equip:"dumbbell", sets:3, reps:15, weight:10 },
  { id:"dip", en:"Triceps dip", tr:"Triceps dalma", muscle:"triceps", equip:"bodyweight", sets:3, reps:12, weight:0 },
  { id:"pushdown", en:"Cable triceps pushdown", tr:"Kablo ile triceps itme", muscle:"triceps", equip:"cable", sets:3, reps:12, weight:32.5 },
  { id:"row", en:"Barbell row", tr:"Halterle kürek çekme", muscle:"back", equip:"barbell", sets:4, reps:8, weight:70 },
  { id:"pulldown", en:"Lat pulldown", tr:"Lat çekiş", muscle:"back", equip:"machine", sets:3, reps:12, weight:55 },
  { id:"squat", en:"Back squat", tr:"Arkadan squat", muscle:"legs", equip:"barbell", sets:5, reps:5, weight:110 },
  { id:"plank", en:"Plank", tr:"Plank", muscle:"core", equip:"bodyweight", sets:3, reps:60, weight:0 }
];

const PLANS = [
  { id:"push", en:"Push Day A", tr:"İt Günü A", minutes:48, ids:["bench","incline","fly","ohp","lateral","pushdown"], today:true },
  { id:"pull", en:"Pull Day A", tr:"Çek Günü A", minutes:44, ids:["row","pulldown","dip"] },
  { id:"legs", en:"Leg Day", tr:"Bacak Günü", minutes:52, ids:["squat","plank"] }
];

const t = (lang, key) => STR[lang][key] || key;
const exName = (lang, o) => o[lang];
const byId = (id) => EXERCISES.find(e => e.id === id);

Object.assign(window, { STR, MUSCLE, EQUIP, EXERCISES, PLANS, t, exName, byId });
